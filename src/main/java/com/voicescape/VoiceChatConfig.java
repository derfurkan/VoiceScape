package com.voicescape;

import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(VoiceChatConfig.CONFIG_GROUP)
public interface VoiceChatConfig extends Config
{
	String CONFIG_GROUP = "voicechat";

	@ConfigItem(keyName = "serverAddress", name = "", description = "", hidden = true)
	default String serverAddress()
	{
		return "voicescape.example.com:5555";
	}

	@ConfigItem(keyName = "autoConnect", name = "", description = "", hidden = true)
	default boolean autoConnect()
	{
		return false;
	}

	@ConfigItem(keyName = "voiceMode", name = "", description = "", hidden = true)
	default VoiceMode voiceMode()
	{
		return VoiceMode.PUSH_TO_TALK;
	}

	@ConfigItem(keyName = "pushToTalkKey", name = "", description = "", hidden = true)
	default Keybind pushToTalkKey()
	{
		return new Keybind(KeyEvent.VK_V, 0);
	}

	@ConfigItem(keyName = "vadSensitivity", name = "", description = "", hidden = true)
	@Range(min = 1, max = 100)
	default int vadSensitivity()
	{
		return 30;
	}

	@ConfigItem(keyName = "voiceRange", name = "", description = "", hidden = true)
	@Range(min = 1, max = 15)
	default int voiceRange()
	{
		return 15;
	}

	@ConfigItem(keyName = "muted", name = "", description = "", hidden = true)
	default boolean muted()
	{
		return false;
	}

	@ConfigItem(keyName = "deafened", name = "", description = "", hidden = true)
	default boolean deafened()
	{
		return false;
	}

	@ConfigItem(keyName = "inputDevice", name = "", description = "", hidden = true)
	default String inputDevice()
	{
		return "";
	}

	@ConfigItem(keyName = "outputDevice", name = "", description = "", hidden = true)
	default String outputDevice()
	{
		return "";
	}

	@ConfigItem(keyName = "micGain", name = "", description = "", hidden = true)
	@Range(min = 0, max = 200)
	default int micGain()
	{
		return 100;
	}

	@ConfigItem(keyName = "outputVolume", name = "", description = "", hidden = true)
	@Range(min = 0, max = 200)
	default int outputVolume()
	{
		return 100;
	}

	@ConfigItem(keyName = "showOverlay", name = "", description = "", hidden = true)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(keyName = "localLoopback", name = "", description = "", hidden = true)
	default boolean localLoopback()
	{
		return false;
	}
	
}
