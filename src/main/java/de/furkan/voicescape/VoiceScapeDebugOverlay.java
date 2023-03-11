package de.furkan.voicescape;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;

import java.awt.*;

public class VoiceScapeDebugOverlay extends OverlayPanel {

    private final Client client;
    private final VoiceScapeConfig config;
    public String lastVoicePacket = !VoiceScapePlugin.isRunning && VoiceScapePlugin.getInstance().voiceReceiver == null ? "Not connected" : "Nothing received";
    long lastVoiceDiff = 0;

    public VoiceScapeDebugOverlay(Client client, VoiceScapeConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        if (config.showDebugInfo()) {
            panelComponent.setPreferredSize(new Dimension(300, 100));
            LineComponent lineComponent = LineComponent.builder()
                    .left("Connected to").right(getCurrentlyConnectedTo())
                    .build();
            LineComponent lineComponent1 = LineComponent.builder().left("Last update packet received").right(getLastUpdate()).build();
            LineComponent lineComponent2 = LineComponent.builder().left("Last voice packet creation age").right(lastVoicePacket).build();
            LineComponent lineComponent3 = LineComponent.builder().left("Sending voice").right(String.valueOf(VoiceScapePlugin.isTalking)).build();
            LineComponent lineComponent4 = LineComponent.builder().left(" ").right(" ").build();
            LineComponent lineComponent5 = LineComponent.builder().left("Voice packet UUID").right(VoiceScapePlugin.uuidString).build();

            panelComponent.getChildren().add(lineComponent);
            panelComponent.getChildren().add(lineComponent1);
            panelComponent.getChildren().add(lineComponent2);
            panelComponent.getChildren().add(lineComponent3);
            panelComponent.getChildren().add(lineComponent4);
            panelComponent.getChildren().add(lineComponent5);
        }
        return super.render(graphics);
    }

    private String getLastUpdate() {
        if (!VoiceScapePlugin.isRunning || VoiceScapePlugin.getInstance().voiceReceiver == null) {
            return "Not connected";
        }
        long diff = System.currentTimeMillis() - VoiceScapePlugin.lastUpdateMessage;

        if (VoiceScapePlugin.lastUpdateMessage == 0) {
            return "Nothing received";
        }


        if (diff < 1000) {
            return "Just now";
        } else {
            return (diff / 1000) + "s ago";
        }
    }

    private String getCurrentlyConnectedTo() {
        if (!VoiceScapePlugin.isRunning || VoiceScapePlugin.getInstance().voiceReceiver == null) {
            return "Not connected";
        }
        switch (config.defaultServers()) {
            case CUSTOM:
                return config.customServerIP();
            case THEBEERKEG:
                return "thebeerkeg.net";
            case VERAC:
                return "verac.pro";
            default:
                return "voicescape.nga.gg";
        }
    }


}
