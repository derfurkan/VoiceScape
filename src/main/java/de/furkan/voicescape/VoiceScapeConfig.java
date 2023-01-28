package de.furkan.voicescape;

import net.runelite.client.config.*;

@ConfigGroup("VoiceScape")
public interface VoiceScapeConfig extends Config {

  @ConfigSection(
      name = "Voice",
      description = "Voice settings",
      position = 1,
      closedByDefault = true)
  String voiceSection = "voice";

  @ConfigSection(name = "Server", description = "Server settings", position = 0)
  String serverSection = "Server";

  @ConfigItem(
      keyName = "loopback",
      name = "Loopback",
      description = "Hear yourself",
      section = voiceSection)
  default boolean loopback() {
    return false;
  }

  @ConfigItem(
      keyName = "muteself",
      name = "Mute Self",
      description = "Mute yourself",
      section = voiceSection)
  default boolean muteSelf() {
    return false;
  }

  @Range(min = 0, max = 100)
  @ConfigItem(
      keyName = "volume",
      name = "Player Volume",
      description = "Volume of other players",
      section = voiceSection)
  @Units(Units.PERCENT)
  default int volume() {
    return 100;
  }

  @Range(min = 2, max = 30)
  @ConfigItem(
      keyName = "distance",
      name = "Min Distance",
      description = "Minimum distance to hear other players",
      section = voiceSection)
  default int minDistance() {
    return 30;
  }

  @ConfigItem(
      keyName = "connected",
      name = "Connect to main server",
      description = "Connected to server",
      section = serverSection,
      position = 0)
  default boolean connected() {
    return false;
  }

  @ConfigItem(
      keyName = "usecustomserver",
      name = "Connect to custom server",
      description = "Connect to custom server",
      section = serverSection)
  default boolean useCustomServer() {
    return false;
  }

  @ConfigItem(
      keyName = "customserverip",
      name = "Custom Server IP",
      description = "The IP of the custom server",
      section = serverSection)
  default String customServerIP() {
    return "127.0.0.1";
  }
}
