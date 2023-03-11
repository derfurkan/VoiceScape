package de.furkan.voicescape;

import net.runelite.client.config.*;

import java.awt.*;

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
            name = "Debug",
            description = "Debug settings",
            position = 4,
            closedByDefault = true)
    String debugSection = "debug";

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
            keyName = "pushtotalk",
            name = "Push-To-Talk",
            description = "If enabled, you have to hold a key to talk",
            section = voiceSection,
            position = 4)
    default boolean pushToTalk() {
        return true;
    }

    @ConfigItem(
            keyName = "pushtotalkkey",
            name = "Push-To-Talk Keybind",
            description = "The keybind to use for push-to-talk",
            section = voiceSection,
            position = 5)
    default Keybind pushToTalkBind() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "defaultserver",
            name = "Default Servers",
            description = "A collection of default servers",
            section = serverSection,
            position = 2)
    default DEFAULT_SERVERS defaultServers() {
        return DEFAULT_SERVERS.CUSTOM;
    }

    @ConfigItem(keyName = " ", name = " ", description = " ", section = serverSection, position = 3)
    default Long spacer() {
        return 2L;
    }

    @ConfigItem(
            keyName = "usecustomserver",
            name = "Connect to server",
            description = "Connect to a server",
            section = serverSection,
            position = 0)
    default boolean useCustomServer() {
        return false;
    }

    @ConfigItem(
            keyName = "customserverip",
            name = "Custom Server IP",
            description = "The IP of the server",
            section = serverSection,
            position = 1)
    default String customServerIP() {
        return "127.0.0.1";
    }

    @ConfigItem(
            keyName = "useproxy",
            name = "Enable Socks5 Proxy",
            description = "Enable Socks5 Proxy to connect to the server",
            section = serverSection,
            position = 4)
    default boolean useProxy() {
        return false;
    }

    @ConfigItem(
            keyName = "proxyipandport",
            name = "Socks5 Proxy IP:Port",
            description = "The IP and Port of the proxy",
            section = serverSection,
            position = 6)
    default String proxyIPAndPort() {
        return "123.456.789:1234";
    }

    @ConfigItem(
            keyName = "proxyusername",
            name = "Socks5 Proxy Username",
            description = "The Username of the proxy",
            section = serverSection,
            position = 7)
    default String proxyUsername() {
        return "";
    }

    @ConfigItem(
            keyName = "proxypassword",
            name = "Socks5 Proxy Password",
            description = "The Password of the proxy",
            section = serverSection,
            position = 8)
    default String proxyPassword() {
        return "";
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
            keyName = "showmuteindicator",
            name = "Show mute indicator",
            description = "Show the mute indicator",
            section = indicatorSection,
            position = 2)
    default boolean showMuteIndicator() {
        return true;
    }

    @ConfigItem(
            keyName = "indicatortype",
            name = "Indicator Type",
            description = "The type of the indicator",
            section = indicatorSection,
            position = 3)
    default INDICATION_TYPE indicatorType() {
        return INDICATION_TYPE.TILE;
    }

    @ConfigItem(
            keyName = "indicatorstring",
            name = "Indicator String",
            description = "The string that will be shown as the indicator",
            section = indicatorSection,
            position = 5)
    default String indicatorString() {
        return "Connected [%p]";
    }

    @ConfigItem(
            keyName = "indicatorcolor",
            name = "Indicator Color",
            description = "The color of the indicator",
            section = indicatorSection,
            position = 6)
    default Color indicatorColor() {
        return Color.YELLOW;
    }

    @Range(min = 2, max = 30)
    @ConfigItem(
            keyName = "indicatordistance",
            name = "Indicator Distance",
            description = "Minimum distance to show the indicator",
            section = indicatorSection,
            position = 4)
    default int indicatorDistance() {
        return 5;
    }

    @ConfigItem(
            keyName = "showdebug",
            name = "Show Debug Info",
            description = "Shows a panel with debug info",
            section = debugSection,
            position = 0)
    default boolean showDebugInfo() {
        return false;
    }

    enum INDICATION_TYPE {
        STRING,
        TILE,
        BOTH
    }

    enum DEFAULT_SERVERS {
        CUSTOM,
        VERAC,
        THEBEERKEG,
        VOICE_NGA
    }
}
