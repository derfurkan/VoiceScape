package de.furkan.voicescape;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class VoiceScapePlugin
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(VoiceScape.class);
		RuneLite.main(args);
	}
}