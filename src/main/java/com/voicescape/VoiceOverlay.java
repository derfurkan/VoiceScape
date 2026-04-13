package com.voicescape;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class VoiceOverlay extends Overlay
{
	private static final Color SPEAKER_COLOR = new Color(0, 200, 83, 200);
	private static final Color SPEAKER_BG = new Color(0, 0, 0, 128);
	private static final int ICON_SIZE = 18;
	private static final int ICON_OFFSET_Y = -8;

	private final Client client;
	private final VoiceChatConfig config;
	private final VoiceChatPlugin plugin;

	@Inject
	public VoiceOverlay(Client client, VoiceChatConfig config, VoiceChatPlugin plugin)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(Overlay.PRIORITY_MED);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		if (plugin.isLocalPlayerTransmitting() && (plugin.getNetworkClient().isConnected() || config.localLoopback()))
		{
			Player local = client.getLocalPlayer();
			if (local != null)
			{
				Point textLocation = local.getCanvasTextLocation(graphics, "", local.getLogicalHeight() + 60);
				if (textLocation != null)
				{
					renderSpeakerIcon(graphics, textLocation);
				}
			}
		}


		Set<String> activeSpeakerHashes = plugin.getActiveSpeakerHashes();
		if (activeSpeakerHashes == null || activeSpeakerHashes.isEmpty())
		{
			return null;
		}

		byte[] dailyKey = plugin.getCurrentDailyKey();
		if (dailyKey == null)
		{
			return null;
		}

		for (Player player : client.getTopLevelWorldView().players())
		{
			if (player == null || player.getName() == null || player == client.getLocalPlayer())
			{
				continue;
			}

			String playerHash = HashUtil.hmac(dailyKey, player.getName());
			if (!activeSpeakerHashes.contains(playerHash))
			{
				continue;
			}

			Point textLocation = player.getCanvasTextLocation(graphics, "", player.getLogicalHeight() + 60);
			if (textLocation == null)
			{
				continue;
			}

			renderSpeakerIcon(graphics, textLocation);
		}

		return null;
	}

	private void renderSpeakerIcon(Graphics2D graphics, Point location)
	{
		int x = location.getX() - ICON_SIZE / 2;
		int y = location.getY() + ICON_OFFSET_Y - ICON_SIZE;

		int pad = 3;
		// Background rounded rectangle — sized to contain the full icon
		graphics.setColor(SPEAKER_BG);
		graphics.fillRoundRect(x - pad, y - pad, ICON_SIZE + pad * 2, ICON_SIZE + pad * 2, 8, 8);

		graphics.setColor(SPEAKER_COLOR);

		// Center the speaker vertically within the icon area
		// Speaker body (small rectangle on the left)
		int bodyW = 4;
		int bodyH = 6;
		int bx = x + 2;
		int by = y + (ICON_SIZE - bodyH) / 2;
		graphics.fillRect(bx, by, bodyW, bodyH);

		// Speaker cone (trapezoid flaring right from the body)
		int coneW = 5;
		int flare = 3; // how much wider the cone is than the body
		int[] triX = {bx + bodyW, bx + bodyW, bx + bodyW + coneW, bx + bodyW + coneW};
		int[] triY = {by, by + bodyH, by + bodyH + flare, by - flare};
		graphics.fillPolygon(triX, triY, 4);

		// Sound waves (arcs) — centered on the cone mouth
		int arcX = bx + bodyW + coneW;
		int arcCenterY = by + bodyH / 2;
		int arcH1 = bodyH + flare * 2 + 2;
		int arcH2 = bodyH + flare * 2 + 6;
		graphics.drawArc(arcX, arcCenterY - arcH1 / 2, 4, arcH1, -50, 100);
		graphics.drawArc(arcX + 3, arcCenterY - arcH2 / 2, 4, arcH2, -50, 100);
	}
}
