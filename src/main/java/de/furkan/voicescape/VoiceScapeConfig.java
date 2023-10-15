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
            name = "Server",
            description = "Server settings",
            position = 0,
            closedByDefault = true)
    String serverSection = "Server";

    @ConfigSection(
            name = "Advanced",
            description = "Advanced settings",
            position = 3,
            closedByDefault = true)
    String advancedSection = "Advanced";

    @ConfigItem(
            keyName = "muteself",
            name = "Mute Self",
            description = "Mute yourself",
            section = voiceSection,
            position = 1)
    default boolean muteSelf() {
        return false;
    }

    @ConfigItem(
            keyName = "muteall",
            name = "Mute Everyone",
            description = "Mute everyone until you unmute them",
            section = voiceSection,
            position = 2)
    default boolean muteAll() {
        return true;
    }

    @Range(min = 5, max = 15)
    @ConfigItem(
            keyName = "distance",
            name = "Min Distance",
            description = "Minimum distance to hear other players",
            section = voiceSection,
            position = 3)
    default int minDistance() {
        return 15;
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
            keyName = "loopback",
            name = "Loopback",
            description = "If enabled, you will hear yourself",
            section = voiceSection,
            position = 6)
    default boolean loopBack() {
        return false;
    }

    @ConfigItem(
            keyName = "servertype",
            name = "Server Type",
            description = "Use Default to connect to the official server, or Custom to connect to a custom server",
            section = serverSection,
            position = 2)
    default SERVER_TYPE serverType() {
        return SERVER_TYPE.NONE;
    }


    @ConfigItem(keyName = " ", name = " ", description = " ", section = serverSection, position = 4)
    default Long spacer() {
        return 2L;
    }

    @ConfigItem(
            keyName = "customserveripandport",
            name = "Custom Server (IP:Port)",
            description = "The IP and port of the custom server",
            position = 7,
            section = serverSection
    )
    default String customServerIPAndPort() {
        return "127.0.0.1:1234";
    }

    @ConfigItem(
            keyName = "customserverpassword",
            name = "Custom Server Password (Optional)",
            description = "The password of the custom server",
            position = 10,
            section = serverSection
    )
    default String customServerPassword() {
        return "";
    }

    @ConfigItem(
            keyName = "customserverusername",
            name = "Custom Server Username (Optional)",
            description = "The username of the custom server",
            position = 9,
            section = serverSection
    )
    default String customServerUsername() {
        return "";
    }


    @ConfigItem(
            keyName = "showwhoisconnected",
            name = "Show connection indicator",
            description = "Show who is connected to the voice chat",
            section = indicatorSection,
            position = 0)
    default boolean connectionIndicator() {
        return true;
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
            keyName = "lowbuffer",
            name = "Low Capture Buffer",
            description = "This will reduce latency when sending voice but can cause your voice sounding choppy.",
            section = advancedSection,
            position = 0)
    default boolean lowCaptureBuffer() {
        return false;
    }

    @ConfigItem(
            keyName = "altplay",
            name = "Alternative Playback",
            description = "This can cause a lot of packet loss if you have a high ping but reduces latency.",
            section = advancedSection,
            position = 1)
    default boolean altPlay() {
        return false;
    }

    @Range(min = 1500, max = 10000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
            keyName = "packetage",
            name = "Max packet age",
            description = "The value in milliseconds a voice packet can be old before it gets dropped on receive. A lower value reduces latency but can drop every voice packet if you have a high ping.",
            section = advancedSection,
            position = 2)
    default int maxPacketAge() {
        return 2000;
    }

    @ConfigItem(
            keyName = "shownetworkovleray",
            name = "Show network overlay",
            description = "Shows a overlay of network information like your ping or packet drops etc.",
            section = advancedSection,
            position = 3)
    default boolean showNetworkOverlay() {
        return false;
    }

    enum INDICATION_TYPE {
        STRING,
        TILE,
        BOTH
    }

    enum SERVER_TYPE {
        DEFAULT,
        CUSTOM,
        NONE
    }
}
