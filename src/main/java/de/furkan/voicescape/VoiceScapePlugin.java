package de.furkan.voicescape;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import javax.swing.*;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@PluginDescriptor(name = "VoiceScape")
public class VoiceScapePlugin extends Plugin {

  private static final VoiceScapePlugin VOICE_SCAPE_PLUGIN_INSTANCE = new VoiceScapePlugin();
  public static boolean isRunning;
  public static ArrayList<Player> nearSpawnedPlayers = Lists.newArrayList();
  private final String mainServerIP = "voicescape.ddns.net";
  public MessageThread messageThread;
  private VoiceEngine voiceEngine;
  @Inject private Client client;
  @com.google.inject.Inject private Gson gson;
  @Inject private VoiceScapeConfig config;

  public static VoiceScapePlugin getInstance() {
    return VOICE_SCAPE_PLUGIN_INSTANCE;
  }

  @Override
  protected void startUp() throws Exception {
    isRunning = true;
    if (client.getGameState() == GameState.LOGGED_IN) {
      if (config.useCustomServer()) runPluginThreads(config.customServerIP());
      else runPluginThreads(mainServerIP);
    }
  }

  @Subscribe
  public void onPlayerSpawned(final PlayerSpawned playerSpawned) {
    nearSpawnedPlayers.add(playerSpawned.getPlayer());
  }

  @Subscribe
  public void onPlayerDespawned(final PlayerDespawned playerDespawned) {
    nearSpawnedPlayers.remove(playerDespawned.getPlayer());
  }

  @Subscribe
  public void onGameStateChanged(final GameStateChanged gameStateChanged) {
    new Timer()
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                if (gameStateChanged.getGameState() == GameState.LOGGED_IN
                    && (voiceEngine == null || messageThread == null)) {
                  if (!config.useCustomServer()) {
                    runPluginThreads(mainServerIP);
                  } else {
                    runPluginThreads(config.customServerIP());
                  }
                }
                if (gameStateChanged.getGameState() != GameState.LOGGED_IN
                    && (voiceEngine != null || messageThread != null)) {
                  shutdownAll();
                }
              }
            },
            3000);
  }

  @Override
  protected void shutDown() throws Exception {
    isRunning = false;
    if (voiceEngine != null || messageThread != null) {
      shutdownAll();
    }
  }

  @Subscribe
  public void onConfigChanged(final ConfigChanged configChanged) {
    if (configChanged.getKey().equals("usecustomserver")) {
      shutdownAll();
      if (!config.useCustomServer()) {
        int option =
            JOptionPane.showConfirmDialog(
                null,
                "Do you want to use the main server?",
                "VoiceScape",
                JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
          runPluginThreads(mainServerIP);
        }
      } else if (config.useCustomServer()) {
        runPluginThreads(config.customServerIP());
      }
    } else if (configChanged.getKey().equals("volume")) {
      if (voiceEngine != null) {
        voiceEngine.voiceReceiverThread.updateSettings();
      }
    } else if (configChanged.getKey().equals("loopback")) {
      if (config.loopback()) {
        nearSpawnedPlayers.add(client.getLocalPlayer());
      } else {
        nearSpawnedPlayers.remove(client.getLocalPlayer());
      }
    }
  }

  private void runPluginThreads(String ip) {
    new Thread(
            () -> {
              messageThread = new MessageThread(ip, 25555, client, config, gson);
              messageThread.completableFuture.whenCompleteAsync(
                  (aBoolean, throwable) -> {
                    if (aBoolean) {
                      voiceEngine = new VoiceEngine(ip, 24444, config, messageThread);
                    }
                  });
            })
        .start();
  }

  private void shutdownAll() {
    System.out.println("Shutting down all threads");
    if (messageThread != null) {
      messageThread.out.println("disconnect");
      messageThread.thread.interrupt();
    }
    voiceEngine.stopEngine();
    voiceEngine = null;
    messageThread = null;
  }

  @Provides
  VoiceScapeConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(VoiceScapeConfig.class);
  }
}
