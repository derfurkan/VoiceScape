package com.voicescape;

import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AudioPlaybackManager extends Thread {
	private static final int MAX_SPEAKERS = 10;
	private static final int FRAME_SIZE_SAMPLES = AudioDeviceManager.FRAME_SIZE_BYTES / 2;

	private final VoiceChatConfig config;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final Map<String, SpeakerState> speakers = new ConcurrentHashMap<>();
	@Getter
	private final Set<String> activeSpeakers = ConcurrentHashMap.newKeySet();
	@Getter
	private final Set<String> mutedHashes = ConcurrentHashMap.newKeySet();
	@Getter
	private final Set<String> mutedDefaultHashes = ConcurrentHashMap.newKeySet();
	@Getter
	private final Set<String> unmutedDefaultHashes = ConcurrentHashMap.newKeySet();
	private volatile Map<String, Integer> nearbyDistances = Collections.emptyMap();
	private SourceDataLine line;

	public AudioPlaybackManager(VoiceChatConfig config) {
		super("VoiceScape-Playback");
		setDaemon(true);
		this.config = config;
	}

	public void flushLine() {
		SourceDataLine l = line;
		if (l != null) {
			l.flush();
		}
	}

	public void mutePlayerDefault(String hash) {
		mutedHashes.add(hash);
	}

	public void mutePlayer(String hash) {
		mutedHashes.add(hash);
	}

	public void unmutePlayer(String hash) {
		mutedHashes.remove(hash);
	}

	public boolean isPlayerMuted(String hash) {
		return mutedHashes.contains(hash) || mutedDefaultHashes.contains(hash);
	}


	public void updateNearbyDistances(Map<String, Integer> distances) {
		this.nearbyDistances = distances;
	}

	public void receiveLoopback(byte[] pcm) {
		if (config.deafened() || line == null || !activeSpeakers.isEmpty()) {
			return;
		}
		
		double volume = config.outputVolume() / 100.0;
		if (volume == 1.0) {
			line.write(pcm, 0, pcm.length);
			return;
		}

		byte[] scaled = new byte[pcm.length];
		for (int i = 0; i < pcm.length - 1; i += 2) {
			int sample = (pcm[i] & 0xFF) | (pcm[i + 1] << 8);
			sample = (int) Math.max(-32768, Math.min(32767, sample * volume));
			scaled[i] = (byte) (sample & 0xFF);
			scaled[i + 1] = (byte) ((sample >> 8) & 0xFF);
		}
		line.write(scaled, 0, scaled.length);
	}

	public void receiveAudio(String senderIdentityHash, int sequenceNumber, byte[] opusPayload) {
		if (config.deafened() || mutedHashes.contains(senderIdentityHash)) {
			return;
		}

		if (!speakers.containsKey(senderIdentityHash) && speakers.size() >= MAX_SPEAKERS) {
			return;
		}

		SpeakerState state = speakers.computeIfAbsent(senderIdentityHash, k -> new SpeakerState());

		state.jitterBuffer.put(sequenceNumber, opusPayload);
		state.lastReceiveTime = System.currentTimeMillis();
		activeSpeakers.add(senderIdentityHash);
	}

	public void openLine() {
		try {
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, AudioDeviceManager.FORMAT);

			String deviceName = config.outputDevice();
			if (deviceName != null && !deviceName.isEmpty()) {
				Mixer.Info mixerInfo = AudioDeviceManager.findMixer(deviceName, false);
				if (mixerInfo != null) {
					Mixer mixer = AudioSystem.getMixer(mixerInfo);
					line = (SourceDataLine) mixer.getLine(info);
				} else {
					log.warn("Output device '{}' not found, falling back to default", deviceName);
					line = (SourceDataLine) AudioSystem.getLine(info);
				}
			} else {
				line = (SourceDataLine) AudioSystem.getLine(info);
			}

			int bufferBytes = AudioDeviceManager.FRAME_SIZE_BYTES * 4;
			line.open(AudioDeviceManager.FORMAT, bufferBytes);
			line.start();
			log.debug("Audio playback line opened (buffer {} bytes)", bufferBytes);
		} catch (Exception e) {
			log.error("Failed to open audio playback line", e);
			line = null;
		}
	}

	@Override
	public void run() {
		running.set(true);
		openLine();

		if (line == null) {
			log.error("No playback line available, playback thread exiting");
			return;
		}

		short[] decodeBuf = new short[FRAME_SIZE_SAMPLES];

		while (running.get()) {
			try {
				float[] mixBuffer = new float[FRAME_SIZE_SAMPLES];
				int senderCount = 0;

				long now = System.currentTimeMillis();
				for (Map.Entry<String, SpeakerState> entry : speakers.entrySet()) {
					SpeakerState state = entry.getValue();

					if (now - state.lastReceiveTime > 500) {
						activeSpeakers.remove(entry.getKey());
						continue;
					}

					Integer distance = nearbyDistances.get(entry.getKey());
					if (distance == null) {
						activeSpeakers.remove(entry.getKey());
						continue;
					}

					float distScale = 1.0f - (float) distance / 15;
					distScale = Math.max(0f, distScale);
					if (distScale <= 0f) {
						continue;
					}

					if (state.jitterBuffer.consumeReset()) {
						state.resetDecoder();
					}

					if (!state.jitterBuffer.isReady() || !state.jitterBuffer.canPoll()) {
						continue;
					}

					byte[] opusPayload = state.jitterBuffer.poll();

					try {
						int decoded;
						if (opusPayload != null) {
							decoded = state.decoder.decode(opusPayload, 0, opusPayload.length,
									decodeBuf, 0, FRAME_SIZE_SAMPLES, false);
						} else {
							decoded = state.decoder.decode(null, 0, 0,
									decodeBuf, 0, FRAME_SIZE_SAMPLES, false);
						}

						for (int i = 0; i < decoded; i++) {
							mixBuffer[i] += decodeBuf[i] * distScale;
						}
						senderCount++;
					} catch (Exception e) {
						log.debug("Opus decode error for sender {}: {}", entry.getKey(), e.getMessage());
					}
				}

				if (senderCount > 0) {
					// Apply output volume and clip to int16
					double volume = config.outputVolume() / 100.0;
					byte[] output = new byte[AudioDeviceManager.FRAME_SIZE_BYTES];
					for (int i = 0; i < mixBuffer.length; i++) {
						int sample = (int) Math.max(-32768, Math.min(32767, mixBuffer[i] * volume));
						output[i * 2] = (byte) (sample & 0xFF);
						output[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
					}
					line.write(output, 0, output.length);
				} else {
					Thread.sleep(5);
				}

				speakers.entrySet().removeIf(e -> {
					if (now - e.getValue().lastReceiveTime > 5000) {
						e.getValue().jitterBuffer.reset();
						return true;
					}
					return false;
				});
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				log.error("Error in audio playback loop", e);
			}
		}

		closeLine();
	}

	public void shutdown() {
		running.set(false);
		this.interrupt();
	}

	public void closeLine() {
		if (line != null) {
			try {
				line.stop();
				line.close();
			} catch (Exception e) {
				log.debug("Error closing playback line", e);
			}
			line = null;
		}
	}

	private static class SpeakerState {
		final JitterBuffer jitterBuffer = new JitterBuffer();
		OpusDecoder decoder;
		volatile long lastReceiveTime = System.currentTimeMillis();

		SpeakerState() {
			try {
				decoder = new OpusDecoder(48000, 1);
			} catch (OpusException e) {
				throw new RuntimeException("Failed to create Opus decoder", e);
			}
		}

		void resetDecoder() {
			try {
				decoder = new OpusDecoder(48000, 1);
			} catch (OpusException e) {
				log.warn("Failed to reset Opus decoder", e);
			}
		}
	}
}
