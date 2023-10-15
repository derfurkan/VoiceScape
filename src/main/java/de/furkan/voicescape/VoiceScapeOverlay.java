package de.furkan.voicescape;

import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;

import java.awt.*;

public class VoiceScapeOverlay extends Overlay {
    private final VoiceScapePlugin voiceScapePlugin;
    public String currentLine = "";

    public VoiceScapeOverlay(VoiceScapePlugin voiceScapePlugin) {
        this.voiceScapePlugin = voiceScapePlugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.MED);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (voiceScapePlugin.config.connectionIndicator() && currentLine.isEmpty()) {
            for (Player player : voiceScapePlugin.client.getPlayers()) {
                if (player.getName() == null
                        || voiceScapePlugin.client.getLocalPlayer().getWorldLocation().distanceTo(player.getWorldLocation())
                        > voiceScapePlugin.config.indicatorDistance()) {
                    continue;
                }
                if (voiceScapePlugin.registeredPlayers.contains(
                        voiceScapePlugin.hashWithSha256(player.getName()))) {
                    if ((voiceScapePlugin.mutedPlayers.contains(voiceScapePlugin.hashWithSha256(player.getName())) || (voiceScapePlugin.config.muteAll() && !voiceScapePlugin.unmutedPlayers.contains(voiceScapePlugin.hashWithSha256(player.getName())) && !player.getName().equals(voiceScapePlugin.client.getLocalPlayer().getName()))) && voiceScapePlugin.config.showMuteIndicator()) {
                        String a = "Muted " + player.getName();
                        int stringLength = graphics.getFontMetrics().stringWidth(a) - 40;
                        net.runelite.api.Point textLocation =
                                player
                                        .getCanvasTextLocation(
                                                graphics,
                                                player.getName(),
                                                player.getLogicalHeight() + 50);
                        if (textLocation == null) return null;
                        textLocation = new net.runelite.api.Point(textLocation.getX() - stringLength / 2, textLocation.getY());
                        OverlayUtil.renderTextLocation(graphics, textLocation, a, voiceScapePlugin.config.indicatorColor());
                    }
                    if (player.getName().equals(voiceScapePlugin.client.getLocalPlayer().getName())) {
                        continue;
                    }
                    if (voiceScapePlugin.config.indicatorType() == VoiceScapeConfig.INDICATION_TYPE.STRING) {
                        renderString(graphics, player);
                    } else if (voiceScapePlugin.config.indicatorType() == VoiceScapeConfig.INDICATION_TYPE.TILE) {
                        renderTile(graphics, player);
                    } else if (voiceScapePlugin.config.indicatorType() == VoiceScapeConfig.INDICATION_TYPE.BOTH) {
                        renderString(graphics, player);
                        renderTile(graphics, player);
                    }
                }
            }
        } else if (!currentLine.equals("")) {
            int stringLength = graphics.getFontMetrics().stringWidth(currentLine) - 40;
            net.runelite.api.Point textLocation =
                    voiceScapePlugin.client
                            .getLocalPlayer()
                            .getCanvasTextLocation(
                                    graphics,
                                    voiceScapePlugin.client.getLocalPlayer().getName(),
                                    voiceScapePlugin.client.getLocalPlayer().getLogicalHeight() + 20);
            if (textLocation == null) return null;
            textLocation = new net.runelite.api.Point(textLocation.getX() - stringLength / 2, textLocation.getY());
            OverlayUtil.renderTextLocation(graphics, textLocation, currentLine, Color.YELLOW);
        }

        return null;
    }

    private void renderString(Graphics2D graphics, Player player) {
        net.runelite.api.Point textLocation =
                player.getCanvasTextLocation(graphics, player.getName(), player.getLogicalHeight() + 20);
        if (textLocation != null) {
            String indicatorText = voiceScapePlugin.config.indicatorString();
            indicatorText = indicatorText.replace("%p", player.getName());
            int stringLength = graphics.getFontMetrics().stringWidth(indicatorText) - 40;
            textLocation = new Point(textLocation.getX() - stringLength / 2, textLocation.getY());

            OverlayUtil.renderTextLocation(
                    graphics, textLocation, indicatorText, voiceScapePlugin.config.indicatorColor());
        }
    }

    private void renderTile(Graphics2D graphics, Player player) {
        OverlayUtil.renderPolygon(graphics, player.getCanvasTilePoly(), voiceScapePlugin.config.indicatorColor());
    }
}