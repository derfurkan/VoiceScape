package com.voicescape;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AudioDeviceManager
{
	static final AudioFormat FORMAT = new AudioFormat(24000, 16, 1, true, false);
	private static final int FRAME_SIZE_SAMPLES = 480;
	static final int FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2;

	public static List<String> getInputDevices()
	{
		List<String> devices = new ArrayList<>();
		DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, FORMAT);

		for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo())
		{
			try
			{
				Mixer mixer = AudioSystem.getMixer(mixerInfo);
				if (mixer.isLineSupported(targetInfo))
				{
					devices.add(mixerInfo.getName());
				}
			}
			catch (Exception e)
			{
				log.debug("Skipping input device {}: {}", mixerInfo.getName(), e.getMessage());
			}
		}

		return devices;
	}

	public static List<String> getOutputDevices()
	{
		List<String> devices = new ArrayList<>();
		DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, FORMAT);

		for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo())
		{
			try
			{
				Mixer mixer = AudioSystem.getMixer(mixerInfo);
				if (mixer.isLineSupported(sourceInfo))
				{
					devices.add(mixerInfo.getName());
				}
			}
			catch (Exception e)
			{
				// Ignored
			}
		}

		return devices;
	}

	public static Mixer.Info findMixer(String name, boolean isInput)
	{
		DataLine.Info lineInfo = isInput
			? new DataLine.Info(TargetDataLine.class, FORMAT)
			: new DataLine.Info(SourceDataLine.class, FORMAT);

		for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo())
		{
			if (mixerInfo.getName().equals(name))
			{
				try
				{
					Mixer mixer = AudioSystem.getMixer(mixerInfo);
					if (mixer.isLineSupported(lineInfo))
					{
						return mixerInfo;
					}
				}
				catch (Exception e)
				{
					log.debug("Mixer {} found but not usable: {}", name, e.getMessage());
				}
			}
		}

		return null;
	}

	public static void bytesToShorts(byte[] bytes, int length, short[] shorts) {
		for (int i = 0; i < length - 1; i += 2) {
			int sample = (bytes[i] & 0xFF) | (bytes[i + 1] << 8);
			if (sample > 32767) sample -= 65536;
			shorts[i / 2] = (short) sample;
		}
	}

	public static void shortsToBytes(short[] shorts, byte[] bytes) {
		for (int i = 0; i < shorts.length; i++) {
			bytes[i * 2] = (byte) (shorts[i] & 0xFF);
			bytes[i * 2 + 1] = (byte) ((shorts[i] >> 8) & 0xFF);
		}
	}
}
