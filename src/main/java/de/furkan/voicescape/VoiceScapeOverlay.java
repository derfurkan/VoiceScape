package de.furkan.voicescape;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayUtil;

import java.awt.*;

public class VoiceScapeOverlay extends Overlay {

    private final Client client;
    private final VoiceScapeConfig config;
    public String currentLine = "";

    public VoiceScapeOverlay(Client client, VoiceScapeConfig config) {
        this.client = client;
        this.config = config;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config.connectionIndicator() && VoiceScapePlugin.isRunning && currentLine.equals("")) {
            for (Player player : client.getPlayers()) {
                if (player.getName() == null
                        || client.getLocalPlayer().getWorldLocation().distanceTo(player.getWorldLocation())
                        > config.indicatorDistance()) {
                    continue;
                }
                if (VoiceScapePlugin.registeredPlayers.contains(
                        VoiceScapePlugin.getInstance().hashWithSha256(player.getName()))) {
                    if (player.getName().equals(client.getLocalPlayer().getName())
                            && !config.showOwnIndicator()) {
                        continue;
                    }
                    if (config.indicatorType() == VoiceScapeConfig.INDICATION_TYPE.STRING) {
                        renderString(graphics, player);
                    } else if (config.indicatorType() == VoiceScapeConfig.INDICATION_TYPE.TILE) {
                        renderTile(graphics, player);
                    } else if (config.indicatorType() == VoiceScapeConfig.INDICATION_TYPE.BOTH) {
                        renderString(graphics, player);
                        renderTile(graphics, player);
                    }
                }
            }
        } else if (!currentLine.equals("")) {
            int stringLength = graphics.getFontMetrics().stringWidth(currentLine) - 40;
            Point textLocation =
                    client
                            .getLocalPlayer()
                            .getCanvasTextLocation(
                                    graphics,
                                    client.getLocalPlayer().getName(),
                                    client.getLocalPlayer().getLogicalHeight() + 60);
            if (textLocation == null) return null;
            textLocation = new Point(textLocation.getX() - stringLength / 2, textLocation.getY());
            OverlayUtil.renderTextLocation(graphics, textLocation, currentLine, Color.YELLOW);
        }

        return null;
    }

    private void renderString(Graphics2D graphics, Player player) {
        Point textLocation =
                player.getCanvasTextLocation(graphics, player.getName(), player.getLogicalHeight() + 60);
        if (textLocation != null) {
            String indicatorText = config.indicatorString();
            indicatorText = indicatorText.replace("%p", player.getName());
            int stringLength = graphics.getFontMetrics().stringWidth(indicatorText) - 40;
            textLocation = new Point(textLocation.getX() - stringLength / 2, textLocation.getY());

            OverlayUtil.renderTextLocation(
                    graphics, textLocation, indicatorText, config.indicatorColor());
        }
    }

    private void renderTile(Graphics2D graphics, Player player) {
        OverlayUtil.renderPolygon(graphics, player.getCanvasTilePoly(), config.indicatorColor());
    }
}
