package com.voicescape;

import net.runelite.client.config.*;

import java.awt.event.KeyEvent;

@ConfigGroup("voicescape")
public interface VoiceChatConfig extends Config
{

	@ConfigItem(keyName = "serverAddress", name = "", description = "", hidden = true)
	default String serverAddress()
	{
		return "voice-scape.de";
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
		return new Keybind(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK);
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
	@Range(max = 200)
	default int micGain()
	{
		return 100;
	}

	@ConfigItem(keyName = "outputVolume", name = "", description = "", hidden = true)
	@Range(max = 200)
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

	@ConfigItem(keyName = "muteAll", name = "", description = "", hidden = true)
	default boolean muteAll()
	{
		return true;
	}
	
}
