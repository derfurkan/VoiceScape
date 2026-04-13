package com.voicescape;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(name = "VoiceScape", description = "Proximity-based voice chat for OSRS", tags = { "voice", "chat",
		"proximity", "audio", "microphone" })
public class VoiceChatPlugin extends Plugin implements KeyListener {
	private static final int HASH_UPDATE_INTERVAL_TICKS = 1;

	@Inject
	private Client client;

	@Inject
	private VoiceChatConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private VoiceOverlay voiceOverlay;

	@Getter
	private NetworkClient networkClient;

	@Getter
	private AudioCaptureThread captureThread;
	@Getter
	private AudioPlaybackManager playbackManager;
	private NavigationButton navButton;
	private VoiceChatPanel panel;

	private volatile boolean pttTransmitting = false;

	private boolean manuallyDisconnect = false;

	private int tickCounter = 0;
	private boolean hadNearbyPlayers = false;

	@Override
	protected void startUp() throws Exception {
		log.info("VoiceScape starting up");

		playbackManager = new AudioPlaybackManager(config);
		networkClient = new NetworkClient(config, playbackManager);
		captureThread = new AudioCaptureThread(config, networkClient, playbackManager);

		panel = new VoiceChatPanel(configManager, config.inputDevice(), config.outputDevice());
		panel.setOnConnect(this::connectToServer);
		panel.setOnDisconnect(() -> {
			if (networkClient != null) {
				manuallyDisconnect = true;
				networkClient.disconnect();
			}
		});
		panel.setPlugin(this);

		networkClient.setStatusListener(msg -> panel.setStatusMessage(msg));

		navButton = NavigationButton.builder()
				.tooltip("VoiceScape")
				.icon(new BufferedImage(48,72,BufferedImage.TYPE_INT_RGB)) // For now
				.priority(10)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);

		overlayManager.add(voiceOverlay);

		keyManager.registerKeyListener(this);

		playbackManager.start();
		captureThread.start();
	}

	@Override
	protected void shutDown() throws Exception {
		log.info("VoiceScape shutting down");

		keyManager.unregisterKeyListener(this);
		overlayManager.remove(voiceOverlay);

		if (navButton != null)
			clientToolbar.removeNavigation(navButton);

		if (networkClient != null)
			networkClient.disconnect();

		if (captureThread != null)
			captureThread.shutdown();

		if (playbackManager != null)
			playbackManager.shutdown();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {
			panel.setConnectButtonState(true);
		} else if (event.getGameState() == GameState.LOGIN_SCREEN) {
			panel.setConnectButtonState(false);
			networkClient.disconnect();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (config.autoConnect() && !networkClient.getRunning().get() && !manuallyDisconnect) {
			Player lp = client.getLocalPlayer();
			if (lp != null && lp.getName() != null) {
				connectToServer();
			}
		}

		tickCounter++;
		if (tickCounter < HASH_UPDATE_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;

		byte[] dailyKey = networkClient.getCurrentDailyKey();
		Player localPlayer = client.getLocalPlayer();
		if (!networkClient.isConnected() || dailyKey == null || localPlayer == null) {
			return;
		}

		WorldPoint localPos = localPlayer.getWorldLocation();
		int range = config.voiceRange();

		List<String> nearbyHashes = new ArrayList<>();
		Map<String, Integer> distanceMap = new HashMap<>();

		distanceMap.put(HashUtil.hmac(dailyKey, localPlayer.getName()), 0);

		for (Player player : client.getTopLevelWorldView().players()) {
			if (player == null || player == localPlayer || player.getName() == null) {
				continue;
			}

			WorldPoint playerPos = player.getWorldLocation();
			if (playerPos.getPlane() != localPos.getPlane()) {
				continue;
			}

			int distance = localPos.distanceTo(playerPos);
			if (distance <= range) {
				String hash = HashUtil.hmac(dailyKey, player.getName());
				distanceMap.put(hash, distance);
				nearbyHashes.add(hash);
			}
		}

		playbackManager.updateNearbyDistances(distanceMap);

		boolean hasNearby = !nearbyHashes.isEmpty();
		captureThread.setHasNearbyPlayers(hasNearby);

		if (hasNearby || hadNearbyPlayers) {
			networkClient.sendHashListUpdate(nearbyHashes);
		}
		hadNearbyPlayers = hasNearby;


		Set<String> activeSpeakers = getActiveSpeakerHashes();
		Set<String> mutedHashes = playbackManager.getMutedHashes();
		Map<String, String> speakerNames = new HashMap<>();
		for (Player player : client.getTopLevelWorldView().players()) {
			if (player == null || player.getName() == null || player == localPlayer) {
				continue;
			}
			String hash = HashUtil.hmac(dailyKey, player.getName());
			if (activeSpeakers.contains(hash) || mutedHashes.contains(hash)) {
				speakerNames.put(player.getName(), hash);
			}
		}
		panel.updatePlayerList(speakerNames);
	}

	@Override
	public void keyPressed(java.awt.event.KeyEvent e) {
		if (config.voiceMode() == VoiceMode.PUSH_TO_TALK && isPttKey(e)) {
			pttTransmitting = true;
			if (captureThread != null) {
				captureThread.setPttActive(true);
			}
		}
	}

	@Override
	public void keyReleased(java.awt.event.KeyEvent e) {
		if (config.voiceMode() == VoiceMode.PUSH_TO_TALK && isPttKey(e)) {
			pttTransmitting = false;
			if (captureThread != null) {
				captureThread.setPttActive(false);
			}
		}
	}

	private boolean isPttKey(java.awt.event.KeyEvent e) {
		Keybind ptt = config.pushToTalkKey();
		if (ptt == null || e.getKeyCode() != ptt.getKeyCode()) {
			return false;
		}
		int requiredMods = ptt.getModifiers();
		if (requiredMods == 0) {
			return true;
		}
		return (e.getModifiersEx() & requiredMods) == requiredMods;
	}

	@Override
	public void keyTyped(java.awt.event.KeyEvent e) {
	}

	public boolean isLocalPlayerTransmitting() {
		if (config.voiceMode() == VoiceMode.PUSH_TO_TALK) {
			return pttTransmitting;
		}

		return captureThread != null && captureThread.isTransmitting();
	}

	public Set<String> getActiveSpeakerHashes() {
		return playbackManager == null ? Collections.emptySet() : playbackManager.getActiveSpeakers();
	}

	public byte[] getCurrentDailyKey() {
		return networkClient == null ? null : networkClient.getCurrentDailyKey();
	}

	public Map<String, String> getNearbyPlayerHashes() {
		byte[] dailyKey = getCurrentDailyKey();
		if (dailyKey == null || client.getLocalPlayer() == null) {
			return Collections.emptyMap();
		}

		Map<String, String> result = new HashMap<>();
		for (Player player : client.getTopLevelWorldView().players()) {
			WorldPoint playerPos = player.getWorldLocation();
			WorldPoint localPos = client.getLocalPlayer().getWorldLocation();
			if (player.getName() == null || player == client.getLocalPlayer() || playerPos.getPlane() != localPos.getPlane()) {
				continue;
			}

			int distance = localPos.distanceTo(playerPos);
			if (distance <= config.voiceRange()) {
				result.put(player.getName(), HashUtil.hmac(dailyKey, player.getName()));
			}
		}
		return result;
	}

	private void connectToServer() {
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || localPlayer.getName() == null) {
			return;
		}
		networkClient.connect(localPlayer.getName());
	}

	@Provides
	VoiceChatConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(VoiceChatConfig.class);
	}
}
