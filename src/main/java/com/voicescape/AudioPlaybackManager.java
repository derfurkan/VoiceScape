package com.voicescape;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
	private final Set<String> unmutedDefaultHashes = ConcurrentHashMap.newKeySet();
	private volatile Map<String, Integer> nearbyDistances = Collections.emptyMap();
	private SourceDataLine line;

	private final short[] decodedBuffer = new short[AudioDeviceManager.FRAME_SIZE_BYTES / 2];
	private final short[] loopbackShortBuffer = new short[AudioDeviceManager.FRAME_SIZE_BYTES / 2];
	private final byte[] outputByteBuffer = new byte[AudioDeviceManager.FRAME_SIZE_BYTES];

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

	public void clearMutedPlayers() {
		mutedHashes.clear();
	}

	public void mutePlayer(String hash) {
		mutedHashes.add(hash);
	}

	public void unmutePlayer(String hash) {
		mutedHashes.remove(hash);
	}

	public boolean isPlayerMuted(String hash) {
		return mutedHashes.contains(hash);
	}

	public void updateNearbyDistances(Map<String, Integer> distances) {
		this.nearbyDistances = distances;
	}

	public void receiveLoopback(byte[] pcm) {
		if (config.deafened() || line == null) {
			return;
		}
		
		double volume = (config.outputVolume() / 100.0);
		
		AudioDeviceManager.bytesToShorts(pcm, pcm.length, loopbackShortBuffer);
		
		for (int i = 0; i < loopbackShortBuffer.length; i++) {
			loopbackShortBuffer[i] = (short) Math.max(-32768, Math.min(32767, loopbackShortBuffer[i] * volume));
		}
		
		AudioDeviceManager.shortsToBytes(loopbackShortBuffer, outputByteBuffer);
		line.write(outputByteBuffer, 0, outputByteBuffer.length);
	}

	public void receiveAudio(String senderIdentityHash, int sequenceNumber, byte[] payload) {
		if (config.deafened() || mutedHashes.contains(senderIdentityHash)) {
			return;
		}

		if (!speakers.containsKey(senderIdentityHash) && speakers.size() >= MAX_SPEAKERS) {
			return;
		}

		SpeakerState state = speakers.computeIfAbsent(senderIdentityHash, k -> new SpeakerState());

		state.jitterBuffer.put(sequenceNumber, payload);
		state.lastReceiveTime = System.currentTimeMillis();
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
					log.debug("Output device '{}' not found, falling back to default", deviceName);
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
			log.debug("Failed to open audio playback line", e);
			line = null;
		}
	}

	@Override
	public void run() {
		running.set(true);
		openLine();

		if (line == null) {
			log.debug("No playback line available, playback thread exiting");
			return;
		}

		while (running.get()) {
			try {
				float[] mixBuffer = new float[FRAME_SIZE_SAMPLES];
				int senderCount = 0;

				long now = System.currentTimeMillis();
				for (Map.Entry<String, SpeakerState> entry : speakers.entrySet()) {
					SpeakerState state = entry.getValue();

					if (now - state.lastReceiveTime > 1000) {
						activeSpeakers.remove(entry.getKey());
						continue;
					}

					Integer distance = nearbyDistances.get(entry.getKey());
					if (distance == null) {
						activeSpeakers.remove(entry.getKey());
						continue;
					}

					float distScale = 1.0f - (float) (Math.log(distance + 1) / Math.log(16));
					distScale = Math.max(0f, distScale);
					if (distScale <= 0f) {
						activeSpeakers.remove(entry.getKey());
						continue;
					}

					if (!state.jitterBuffer.isReady() || !state.jitterBuffer.canPoll()) {
						continue;
					}

					byte[] payload = state.jitterBuffer.poll();

					try {
						if (payload == null)
							continue;

						AudioCodec.decode(payload, 0, payload.length, decodedBuffer);

						for (int i = 0; i < decodedBuffer.length; i++) {
							if (i < mixBuffer.length) {
								mixBuffer[i] += decodedBuffer[i] * distScale;
							}
						}
						senderCount++;
						activeSpeakers.add(entry.getKey());
					} catch (Exception e) {
						log.debug("Audio processing error for sender {}: {}", entry.getKey(), e.getMessage());
					}
				}

				if (senderCount > 0) {
					double volume = config.outputVolume() / 100.0;
					
					float maxVal = 0;
					for (float v : mixBuffer) {
						maxVal = Math.max(maxVal, Math.abs(v));
					}
					
					float masterScale = 1.0f;
					if (maxVal > 32767) {
						masterScale = 32767f / maxVal;
					}

					for (int i = 0; i < mixBuffer.length; i++) {
						loopbackShortBuffer[i] = (short) Math.max(-32768, Math.min(32767, mixBuffer[i] * masterScale * volume));
					}
					
					AudioDeviceManager.shortsToBytes(loopbackShortBuffer, outputByteBuffer);
					line.write(outputByteBuffer, 0, outputByteBuffer.length);
				} else {
					Thread.sleep(10);
				}

				// Cleanup speaker
				speakers.entrySet().removeIf(e -> {
					if (now - e.getValue().lastReceiveTime > 5000) {
						activeSpeakers.remove(e.getKey());
						e.getValue().jitterBuffer.reset();
						return true;
					}
					return false;
				});
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				log.debug("Error in audio playback loop", e);
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
		volatile long lastReceiveTime = System.currentTimeMillis();
	}
}
