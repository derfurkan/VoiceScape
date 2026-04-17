package com.voicescape;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.util.*;

@Slf4j
@PluginDescriptor(name = "VoiceScape", description = "Proximity-based voice chat for OSRS", tags = { "voice", "chat",
		"proximity", "audio", "microphone" })
public class VoiceChatPlugin extends Plugin {
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

	private final HotkeyListener pttListener = new HotkeyListener(() -> config.pushToTalkKey()) {
		@Override
		public void hotkeyPressed() {
			if (config.voiceMode() != VoiceMode.PUSH_TO_TALK) {
				return;
			}
			pttTransmitting = true;
			if (captureThread != null) {
				captureThread.setPttActive(true);
			}
		}

		@Override
		public void hotkeyReleased() {
			if (config.voiceMode() != VoiceMode.PUSH_TO_TALK) {
				return;
			}
			pttTransmitting = false;
			if (captureThread != null) {
				captureThread.setPttActive(false);
			}
		}
	};

	private boolean manuallyDisconnect = false;

	private int tickCounter = 0;
	private boolean hadNearbyPlayers = false;

	private final Map<String, String> playerHashCache = new HashMap<>();
	private byte[] lastDailyKey = null;

	@Override
	protected void startUp() throws Exception {
		log.info("VoiceScape starting");

		playbackManager = new AudioPlaybackManager(config);
		networkClient = new NetworkClient(config, playbackManager);
		captureThread = new AudioCaptureThread(config, networkClient, playbackManager);

		panel = new VoiceChatPanel(configManager, config.inputDevice(), config.outputDevice());
		panel.setOnConnect(() -> {
			manuallyDisconnect = false;
			connectToServer();
		});

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
				.icon(ImageIO.read(getClass().getResourceAsStream("/icon.png")))
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);

		overlayManager.add(voiceOverlay);

		keyManager.registerKeyListener(pttListener);

		playbackManager.start();
		captureThread.start();
	}

	@Override
	protected void shutDown() {
		log.info("VoiceScape shutting down");

		keyManager.unregisterKeyListener(pttListener);
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
			manuallyDisconnect = false;
			panel.setConnectButtonState(false);
			networkClient.disconnect();
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {
		Player localPlayer = client.getLocalPlayer();
		if (config.autoConnect() && !networkClient.getRunning().get() && !manuallyDisconnect) {
			if (localPlayer != null && localPlayer.getName() != null) {
				connectToServer();
			}
		}

		tickCounter++;
		if (tickCounter < HASH_UPDATE_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;

		byte[] dailyKey = networkClient.getCurrentDailyKey();
		if (!networkClient.isConnected() || dailyKey == null || localPlayer == null) {
			return;
		}

		if (dailyKey != lastDailyKey) {
			playerHashCache.clear();
			lastDailyKey = dailyKey;
		}

		WorldPoint localPos = localPlayer.getWorldLocation();
		int range = config.voiceRange();

		List<String> nearbyHashes = new ArrayList<>();
		Map<String, Integer> distanceMap = new HashMap<>();
		Set<String> activeSpeakers = getActiveSpeakerHashes();
		Map<String, String> speakerNames = new HashMap<>();

		distanceMap.put(getOrCreateHash(localPlayer.getName()), 0);

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
				String hash = getOrCreateHash(player.getName());
				distanceMap.put(hash, distance);
				nearbyHashes.add(hash);

				if (config.muteAll() && !playbackManager.isPlayerMuted(hash)
						&& !playbackManager.getUnmutedDefaultHashes().contains(hash)) {
					playbackManager.mutePlayer(hash);
				}

				if (activeSpeakers.contains(hash)) {
					speakerNames.put(player.getName(), hash);
				}
			}
		}

		playbackManager.updateNearbyDistances(distanceMap);

		boolean hasNearby = !nearbyHashes.isEmpty();
		captureThread.setHasNearbyPlayers(hasNearby);

		if (hasNearby || hadNearbyPlayers) {
			networkClient.sendHashListUpdate(nearbyHashes);
		}
		hadNearbyPlayers = hasNearby;
		panel.updatePlayerList(speakerNames);
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


	private String getOrCreateHash(String name) {
		byte[] key = networkClient.getCurrentDailyKey();
		if (key == null) return "";
		return playerHashCache.computeIfAbsent(name, n -> HashUtil.hmac(key, n));
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
