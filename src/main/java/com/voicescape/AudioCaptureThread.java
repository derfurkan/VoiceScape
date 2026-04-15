package com.voicescape;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;
import io.github.jaredmdobson.concentus.OpusSignal;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class AudioCaptureThread extends Thread
{
	private final VoiceChatConfig config;
	private final NetworkClient networkClient;
	private final AudioPlaybackManager playbackManager;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private static final int FRAME_SIZE_SAMPLES = AudioDeviceManager.FRAME_SIZE_BYTES / 2;
	private static final int OPUS_BITRATE = 24000;

	private static final int VAD_HANGOVER_FRAMES = 30;
	private static final int VAD_PREROLL_FRAMES = 10;

	private static final int TAIL_SILENCE_FRAMES = 5;

	@Setter
    private volatile boolean pttActive = false;
	@Getter
    private volatile boolean transmitting = false;
	@Setter
    private volatile boolean hasNearbyPlayers = false;
	private boolean wasTransmitting = false;
	private int vadHangoverRemaining = 0;
	private int tailSilenceRemaining = 0;
	private final byte[][] prerollBuffer = new byte[VAD_PREROLL_FRAMES][];
	private int prerollIndex = 0;
	private int prerollCount = 0;
	private TargetDataLine line;
	private OpusEncoder opusEncoder;

	public AudioCaptureThread(VoiceChatConfig config, NetworkClient networkClient, AudioPlaybackManager playbackManager)
	{
		super("VoiceScape-Capture");
		setDaemon(true);
		this.config = config;
		this.networkClient = networkClient;
		this.playbackManager = playbackManager;
	}

    public void openLine()
	{
		try
		{
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, AudioDeviceManager.FORMAT);

			String deviceName = config.inputDevice();
			if (deviceName != null && !deviceName.isEmpty())
			{
				Mixer.Info mixerInfo = AudioDeviceManager.findMixer(deviceName, true);
				if (mixerInfo != null)
				{
					Mixer mixer = AudioSystem.getMixer(mixerInfo);
					line = (TargetDataLine) mixer.getLine(info);
				}
				else
				{
					log.warn("Input device '{}' not found, falling back to default", deviceName);
					line = (TargetDataLine) AudioSystem.getLine(info);
				}
			}
			else
			{
				line = (TargetDataLine) AudioSystem.getLine(info);
			}

			line.open(AudioDeviceManager.FORMAT);
			line.start();
			log.debug("Audio capture line opened");
		}
		catch (Exception e)
		{
			log.error("Failed to open audio capture line", e);
			line = null;
		}
	}

	@Override
	public void run()
	{
		running.set(true);
		openLine();

		if (line == null)
		{
			log.error("No capture line available, capture thread exiting");
			return;
		}

		try
		{
			opusEncoder = new OpusEncoder(48000, 1, OpusApplication.OPUS_APPLICATION_VOIP);
			opusEncoder.setBitrate(OPUS_BITRATE);
			opusEncoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
			opusEncoder.setComplexity(5);
		}
		catch (Exception e)
		{
			log.error("Failed to create Opus encoder", e);
			closeLine();
			return;
		}

		byte[] buffer = new byte[AudioDeviceManager.FRAME_SIZE_BYTES];
		byte[] opusBuffer = new byte[200];

		while (running.get())
		{
			try
			{
				if(line == null)
					continue;
				int bytesRead = line.read(buffer, 0, buffer.length);
				if (bytesRead <= 0)
				{
					continue;
				}

				if (config.muted())
				{
					transmitting = false;
					continue;
				}

				boolean shouldTransmit;
				boolean sendSilence = false;
				if (config.voiceMode() == VoiceMode.PUSH_TO_TALK)
				{
					if (pttActive)
					{
						vadHangoverRemaining = VAD_HANGOVER_FRAMES;
						tailSilenceRemaining = TAIL_SILENCE_FRAMES;
						shouldTransmit = true;
					}
					else if (vadHangoverRemaining > 0)
					{
						vadHangoverRemaining--;
						shouldTransmit = true;
					}
					else if (tailSilenceRemaining > 0)
					{
						tailSilenceRemaining--;
						shouldTransmit = false;
						sendSilence = true;
					}
					else
					{
						shouldTransmit = false;
					}
				}
				else
				{
					if (isAboveVadThreshold(buffer, bytesRead))
					{
						vadHangoverRemaining = VAD_HANGOVER_FRAMES;
						tailSilenceRemaining = TAIL_SILENCE_FRAMES;
						shouldTransmit = true;
					}
					else if (vadHangoverRemaining > 0)
					{
						vadHangoverRemaining--;
						shouldTransmit = true;
					}
					else if (tailSilenceRemaining > 0)
					{
						tailSilenceRemaining--;
						shouldTransmit = false;
						sendSilence = true;
					}
					else
					{
						shouldTransmit = false;
					}
				}

				transmitting = shouldTransmit || sendSilence;

				byte[] pcm = applyGain(buffer, bytesRead, config.micGain());

				if (sendSilence)
				{
					if (networkClient.isConnected() && hasNearbyPlayers)
					{
						try
						{
							byte[] silence = new byte[AudioDeviceManager.FRAME_SIZE_BYTES];
							encodeAndSend(opusBuffer, silence);
						}
						catch (Exception e)
						{
							// Ignored
						}
					}
					if (tailSilenceRemaining == 0)
					{
						wasTransmitting = false;
						playbackManager.flushLine();
					}
					continue;
				}

				if (!shouldTransmit)
				{
					if (config.voiceMode() != VoiceMode.PUSH_TO_TALK)
					{
						prerollBuffer[prerollIndex] = pcm == buffer ? buffer.clone() : pcm;
						prerollIndex = (prerollIndex + 1) % VAD_PREROLL_FRAMES;
						if (prerollCount < VAD_PREROLL_FRAMES)
						{
							prerollCount++;
						}
					}

					if (wasTransmitting)
					{
						wasTransmitting = false;
						playbackManager.flushLine();
					}
					continue;
				}
				
				if (!wasTransmitting && prerollCount > 0)
				{
					int start = (prerollIndex - prerollCount + VAD_PREROLL_FRAMES) % VAD_PREROLL_FRAMES;
					for (int i = 0; i < prerollCount; i++)
					{
						int idx = (start + i) % VAD_PREROLL_FRAMES;
						byte[] prerollPcm = prerollBuffer[idx];
						if (prerollPcm == null)
						{
							continue;
						}
						prerollBuffer[idx] = null;

						if (config.localLoopback())
						{
							playbackManager.receiveLoopback(prerollPcm);
						}

						if (networkClient.isConnected() && hasNearbyPlayers)
						{
							try
							{
								encodeAndSend(opusBuffer, prerollPcm);
							}
							catch (Exception e)
							{
								// Ignored
							}
						}
					}
					prerollCount = 0;
				}
				wasTransmitting = true;

				if (config.localLoopback())
				{
					playbackManager.receiveLoopback(pcm);
				}

				if (networkClient.isConnected() && hasNearbyPlayers)
				{
					try
					{
						int encodedBytes = opusEncoder.encode(pcm, 0, FRAME_SIZE_SAMPLES,
							opusBuffer, 0, opusBuffer.length);
						if (encodedBytes > 0)
						{
							byte[] opusPayload = new byte[encodedBytes];
							System.arraycopy(opusBuffer, 0, opusPayload, 0, encodedBytes);
							networkClient.sendAudioFrame(networkClient.nextSequenceNumber(), opusPayload);
						}
					}
					catch (Exception e)
					{
						log.warn("Opus encode failed: {}", e.getMessage());
					}
				}
			}
			catch (Exception e)
			{
				log.error("Error in audio capture loop", e);
			}
		}

		closeLine();
	}

	private void encodeAndSend(byte[] opusBuffer, byte[] silence) throws OpusException {
		int enc = opusEncoder.encode(silence, 0, FRAME_SIZE_SAMPLES,
			opusBuffer, 0, opusBuffer.length);
		if (enc > 0)
		{
			byte[] payload = new byte[enc];
			System.arraycopy(opusBuffer, 0, payload, 0, enc);
			networkClient.sendAudioFrame(networkClient.nextSequenceNumber(), payload);
		}
	}

	public void shutdown()
	{
		running.set(false);
		this.interrupt();
	}

	public void closeLine()
	{
		if (line != null)
		{
			try
			{
				line.stop();
				line.close();
			}
			catch (Exception e)
			{
				log.debug("Error closing capture line", e);
			}
			line = null;
		}
	}

	private boolean isAboveVadThreshold(byte[] buffer, int length)
	{
		double rms = 0;
		int samples = length / 2;
		for (int i = 0; i < length - 1; i += 2)
		{
			int sample = (buffer[i] & 0xFF) | (buffer[i + 1] << 8);
			rms += sample * sample;
		}
		rms = Math.sqrt(rms / samples);

		double t = config.vadSensitivity() / 100.0;
		double threshold = 5.0 + (t * t) * 10000;
		return rms > threshold;
	}

	private byte[] applyGain(byte[] buffer, int length, int gainPercent)
	{
		if (gainPercent == 100)
		{
			return buffer;
		}

		double gain = gainPercent / 100.0;
		byte[] output = new byte[length];
		for (int i = 0; i < length - 1; i += 2)
		{
			int sample = (buffer[i] & 0xFF) | (buffer[i + 1] << 8);
			sample = (int) Math.max(-32768, Math.min(32767, sample * gain));
			output[i] = (byte) (sample & 0xFF);
			output[i + 1] = (byte) ((sample >> 8) & 0xFF);
		}
		return output;
	}
}
