package com.voicescape;


import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class AudioCaptureThread extends Thread
{
	private final VoiceChatConfig config;
	private final NetworkClient networkClient;
	private final AudioPlaybackManager playbackManager;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private static final int VAD_HANGOVER_FRAMES = 30;
	private static final int VAD_PREROLL_FRAMES = 5;

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

	private final short[] pcmShortsBuffer = new short[AudioDeviceManager.FRAME_SIZE_BYTES / 2];
	private final byte[] gainByteBuffer = new byte[AudioDeviceManager.FRAME_SIZE_BYTES];
	private final byte[] losslessOutputBuffer = new byte[AudioDeviceManager.FRAME_SIZE_BYTES * 3];

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
					log.debug("Input device '{}' not found, falling back to default", deviceName);
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
			log.debug("Failed to open audio capture line", e);
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
			log.debug("No capture line available, capture thread exiting");
			return;
		}

		byte[] buffer = new byte[AudioDeviceManager.FRAME_SIZE_BYTES];

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

				double rms = calculateRmsAndApplyGain(buffer, bytesRead, gainByteBuffer, config.micGain());

				boolean shouldTransmit = false;
				boolean sendSilence = false;
				
				if ((config.voiceMode() == VoiceMode.PUSH_TO_TALK && pttActive) || (config.voiceMode() == VoiceMode.VOICE_ACTIVITY && isAboveThreshold(rms)))
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
                        sendSilence = true;
					}
				}

				transmitting = shouldTransmit || sendSilence;

				byte[] pcm = gainByteBuffer;

				if (sendSilence)
				{
					if (networkClient.isConnected() && hasNearbyPlayers)
					{
						try
						{
                            Arrays.fill(gainByteBuffer, (byte) 0);
							encodeAndSend(gainByteBuffer);
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
						prerollBuffer[prerollIndex] = pcm.clone();
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
								encodeAndSend(prerollPcm);
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
					encodeAndSend(pcm);
				}
			}
			catch (Exception e)
			{
				log.debug("Error in audio capture loop", e);
			}
		}

		closeLine();
	}

	private void encodeAndSend(byte[] pcm) {
		AudioDeviceManager.bytesToShorts(pcm, pcm.length, pcmShortsBuffer);
		int encodedLength = AudioCodec.encode(pcmShortsBuffer, losslessOutputBuffer);

		byte[] finalPayload = new byte[encodedLength];
		System.arraycopy(losslessOutputBuffer, 0, finalPayload, 0, encodedLength);

		networkClient.sendAudioFrame(networkClient.nextSequenceNumber(), finalPayload);
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

	private double calculateRmsAndApplyGain(byte[] input, int length, byte[] output, int gainPercent) {
		double rms = 0;
		int samples = length / 2;
		double gain = gainPercent / 100.0;

		for (int i = 0; i < length - 1; i += 2) {
			int sample = (input[i] & 0xFF) | (input[i + 1] << 8);
			if (sample > 32767) sample -= 65536;

			rms += (double)sample * sample;
			if (gain != 1.0) {
				sample = (int) Math.max(-32768, Math.min(32767, sample * gain));
			}
			
			output[i] = (byte) (sample & 0xFF);
			output[i + 1] = (byte) ((sample >> 8) & 0xFF);
		}
		return Math.sqrt(rms / samples);
	}

	private boolean isAboveThreshold(double rms)
	{
		double t = config.vadSensitivity() / 100.0;
		double threshold = 5.0 + (t * t) * 10000;
		return rms > threshold;
	}
}

