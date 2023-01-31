package de.furkan.voicescape;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.menus.MenuManager;
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
  @Inject private MenuManager menuManager;



  public static VoiceScapePlugin getInstance() {
    return VOICE_SCAPE_PLUGIN_INSTANCE;
  }

  @Override
  protected void startUp() throws Exception {
    menuManager.addPlayerMenuItem("Mute");
    menuManager.addPlayerMenuItem("Unmute");

    isRunning = true;
    if (client.getGameState() == GameState.LOGGED_IN) {
      if (voiceEngine != null && messageThread != null) {
        shutdownAll();
      }
      if (config.useCustomServer()) runPluginThreads(config.customServerIP());
      else runPluginThreads(mainServerIP);
    }
  }


  @Subscribe
  public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked) {
    if (messageThread != null && (menuOptionClicked.getMenuOption().equals("Mute") || menuOptionClicked.getMenuOption().equals("Unmute")) && menuOptionClicked.getMenuTarget().contains(">") && menuOptionClicked.getMenuTarget().contains("<")) {
      String command = menuOptionClicked.getMenuOption().equals("Mute") ? "mute " : "unmute ";
      messageThread.out.println(command + menuOptionClicked.getMenuTarget().split(">")[1].split("<")[0]);
      client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",menuOptionClicked.getMenuOption().equals("Mute") ? "Muted " + menuOptionClicked.getMenuTarget().split(">")[1].split("<")[0] : "Unmuted " + menuOptionClicked.getMenuTarget().split(">")[1].split("<")[0], "");
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

  // Useless code for now but will be used later on for muting and unmuting players.

  /*  @Subscribe
  public void onCommandExecuted(net.runelite.api.events. commandExecuted) {
    System.out.println(commandExecuted.getCommand());
    if (commandExecuted.getCommand().contains(" ") && (commandExecuted.getCommand().split(" ")[0].equals("mute") || commandExecuted.getCommand().split(" ")[0].equals("unmute"))
        ) {
      String command = commandExecuted.getCommand().split(" ")[0];
      String playerName = commandExecuted.getCommand().split(" ")[1];
      if (messageThread != null && messageThread.out != null) {
        messageThread.out.println(command + " " + playerName);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", command.equals("mute") ? "Muted " + playerName : "Unmuted " + playerName, "");
      } else {
        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE, "", "You are not connected to a server!", "");
      }
    }

  }*/

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
      if (config.loopback() && !nearSpawnedPlayers.contains(client.getLocalPlayer())) {
        nearSpawnedPlayers.add(client.getLocalPlayer());
      } else {
        nearSpawnedPlayers.remove(client.getLocalPlayer());
      }
    }
  }

  private void runPluginThreads(String ip) {
    new Thread(
            () -> {
              voiceEngine = new VoiceEngine(ip, 24444, config);
              voiceEngine.completableFuture.whenCompleteAsync(
                  (aBoolean, throwable) -> {
                    if (aBoolean) {
                      messageThread = new MessageThread(ip, 25555, client, config, gson);
                      voiceEngine.messageThread = messageThread;
                    }
                  });
            })
        .start();
  }

  private void shutdownAll() {
    if (messageThread != null) messageThread.thread.interrupt();
    if (messageThread != null && messageThread.out != null) messageThread.out.println("disconnect");

    voiceEngine.stopEngine();
    voiceEngine = null;
    messageThread = null;
  }

  @Provides
  VoiceScapeConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(VoiceScapeConfig.class);
  }
}
