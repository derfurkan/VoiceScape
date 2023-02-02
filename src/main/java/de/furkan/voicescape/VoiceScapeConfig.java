package de.furkan.voicescape;

import net.runelite.client.config.*;

@ConfigGroup("VoiceScape")
public interface VoiceScapeConfig extends Config {

  @ConfigSection(
      name = "Voice Chat",
      description = "Voice settings",
      position = 1)
  String voiceSection = "voice";

  @ConfigSection(
          name = "Other",
          description = "Other settings",
          position = 2,
          closedByDefault = false)
  String otherSection = "other";

  @ConfigSection(name = "Server", description = "Server settings", position = 0,closedByDefault = true)
  String serverSection = "Server";

  @ConfigItem(
      keyName = "loopback",
      name = "Loopback",
      description = "Hear yourself",
      section = voiceSection,
  position = 0)
  default boolean loopback() {
    return false;
  }

  @ConfigItem(
      keyName = "muteself",
      name = "Mute",
      description = "Mute yourself",
      section = voiceSection,
  position = 1)
  default boolean muteSelf() {
    return false;
  }

  @Range(min = 0, max = 100)
  @ConfigItem(
      keyName = "volume",
      name = "Player Volume",
      description = "Volume of other players",
      section = voiceSection,
  position = 2)
  @Units(Units.PERCENT)
  default int volume() {
    return 100;
  }

  @Range(min = 2, max = 30)
  @ConfigItem(
      keyName = "distance",
      name = "Min Distance",
      description = "Minimum distance to hear other players",
      section = voiceSection,
  position = 3)
  default int minDistance() {
    return 30;
  }

  @ConfigItem(
      keyName = "usecustomserver",
      name = "Connect to custom server instead",
      description = "Connect to custom server instead of the default one",
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

  @ConfigItem(
          keyName = "performancemode",
          name = "Performance Mode",
          description = "Use this if you have a bad internet connection or a slow computer",
          section = otherSection)
  default boolean performanceMode() {
    return false;
  }
}
