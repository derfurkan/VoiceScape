package de.furkan.voicescape;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;

import java.awt.*;

public class VoiceScapeNetworkOverlay extends OverlayPanel {
    private final VoiceScapePlugin voiceScapePlugin;
    public VoiceScapeNetworkOverlay(VoiceScapePlugin voiceScapePlugin) {
        this.voiceScapePlugin = voiceScapePlugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        if (voiceScapePlugin.config.showNetworkOverlay()) {
            panelComponent.setPreferredSize(new Dimension(300, 100));
            LineComponent lineComponent1 = LineComponent.builder().left("Ping").right(voiceScapePlugin.pingInMs == 0 ? "calculating" : voiceScapePlugin.pingInMs + "ms").build();
            LineComponent lineComponent3 = LineComponent.builder().left("Dropping Packets").right(voiceScapePlugin.droppingPacket ? "Yes" : "No").build();
            LineComponent lineComponent2 = LineComponent.builder().left("Registered Players").right(String.valueOf(voiceScapePlugin.registeredPlayers.size())).build();
            LineComponent lineComponent4 = LineComponent.builder().left(" ").right(" ").build();

            panelComponent.getChildren().add(lineComponent1);
            panelComponent.getChildren().add(lineComponent2);
            panelComponent.getChildren().add(lineComponent3);
            panelComponent.getChildren().add(lineComponent4);
        }
        return super.render(graphics);
    }


}
