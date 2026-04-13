package com.voicescape;

import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AudioDeviceManager
{
	static final AudioFormat FORMAT = new AudioFormat(48000, 16, 1, true, false);
	private static final int FRAME_SIZE_SAMPLES = 960;
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
					log.warn("Mixer {} found but not usable: {}", name, e.getMessage());
				}
			}
		}

		return null;
	}
}
