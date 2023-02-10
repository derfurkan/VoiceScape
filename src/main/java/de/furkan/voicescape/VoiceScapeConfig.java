package de.furkan.voicescape;

import net.runelite.client.config.*;

@ConfigGroup("VoiceScape")
public interface VoiceScapeConfig extends Config {

  @ConfigSection(name = "Voice Chat", description = "Voice settings", position = 1)
  String voiceSection = "voice";

  @ConfigSection(
      name = "Indicator",
      description = "Connection indicator settings",
      position = 2,
      closedByDefault = true)
  String indicatorSection = "indicator";

  @ConfigSection(
      name = "Performance",
      description = "Performance settings",
      position = 3,
      closedByDefault = true)
  String performanceSection = "performance";

  @ConfigSection(
      name = "Server",
      description = "Server settings",
      position = 0,
      closedByDefault = true)
  String serverSection = "Server";

  @ConfigItem(
      keyName = "muteself",
      name = "Mute",
      description = "Mute yourself",
      section = voiceSection,
      position = 1)
  default boolean muteSelf() {
    return false;
  }

  @Range(min = 1, max = 100)
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
      section = performanceSection,
      position = 0)
  default boolean performanceMode() {
    return false;
  }

  @Range(min = 2, max = 30)
  @ConfigItem(
      keyName = "maxclients",
      name = "Max Clients",
      description = "The maximum amount of clients that you can hear at the same time",
      section = performanceSection,
      position = 1)
  default int maxClients() {
    return 5;
  }

  @ConfigItem(
      keyName = "showwhoisconnected",
      name = "Connection indicator",
      description = "Show who is connected to the voice chat",
      section = indicatorSection,
      position = 0)
  default boolean connectionIndicator() {
    return true;
  }

  @ConfigItem(
      keyName = "showownindicator",
      name = "Show own indicator",
      description = "Show your own indicator",
      section = indicatorSection,
      position = 1)
  default boolean showOwnIndicator() {
    return false;
  }

  @ConfigItem(
      keyName = "indicatorstring",
      name = "Indicator",
      description = "The string that will be shown in the indicator",
      section = indicatorSection,
      position = 3)
  default String indicatorString() {
    return "Connected [%p]";
  }

  @Range(min = 2, max = 30)
  @ConfigItem(
      keyName = "indicatordistance",
      name = "Indicator Distance",
      description = "Minimum distance to show the indicator",
      section = indicatorSection,
      position = 2)
  default int indicatorDistance() {
    return 5;
  }
}
