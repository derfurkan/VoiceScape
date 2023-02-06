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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import javax.swing.*;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@PluginDescriptor(name = "VoiceScape")
public class VoiceScapePlugin extends Plugin {

  private static final VoiceScapePlugin VOICE_SCAPE_PLUGIN_INSTANCE = new VoiceScapePlugin();
  public static boolean isRunning;
  public static ArrayList<String> registeredPlayers = Lists.newArrayList();

  public static ArrayList<Player> indicatedPlayers = Lists.newArrayList();
  private final String mainServerIP = "127.0.0.1";
  public MessageThread messageThread;
  public String microphoneName;
  public String speakerName;
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

    if (AudioSystem.getMixerInfo().length == 0) {
      JOptionPane.showMessageDialog(
          null,
          "No audio devices found. Please check your audio settings.",
          "VoiceScape - No audio devices found!",
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    new Timer()
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                SwingUtilities.invokeLater(
                    () -> {
                      showMicrophoneSelectionPrompt();
                      showSpeakerSelectionPrompt();

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
                    });
              }
            },
            2000);
  }

  private void showMicrophoneSelectionPrompt() {

    ArrayList<String> microphones = Lists.newArrayList();

    for (Mixer.Info info : AudioSystem.getMixerInfo()) {
      if (info.getDescription().toLowerCase().contains("capture")) microphones.add(info.getName());
    }

    String[] options = new String[microphones.size()];
    for (int i = 0; i < microphones.size(); i++) {
      options[i] = "<html><font color=white>" + microphones.get(i) + "</font></html>";
    }
    String input =
        (String)
            JOptionPane.showInputDialog(
                null,
                "Select your microphone",
                "VoiceScape - Microphone Selection",
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

    if (input != null) {
      for (String microphone : microphones) {
        if (microphone.equals(input)) {
          microphoneName = microphone;
          break;
        }
      }
    } else {
      JOptionPane.showMessageDialog(
          null,
          "No microphone selected. Please select a microphone.",
          "VoiceScape - No microphone selected!",
          JOptionPane.ERROR_MESSAGE);
      showMicrophoneSelectionPrompt();
    }
  }

  private void showSpeakerSelectionPrompt() {

    ArrayList<String> speakers = Lists.newArrayList();

    for (Mixer.Info info : AudioSystem.getMixerInfo()) {
      if (info.getDescription().toLowerCase().contains("playback")) speakers.add(info.getName());
    }

    String[] options = new String[speakers.size()];
    for (int i = 0; i < speakers.size(); i++) {
      options[i] = "<html><font color=white>" + speakers.get(i) + "</font></html>";
    }
    String input =
        (String)
            JOptionPane.showInputDialog(
                null,
                "Select your speaker",
                "VoiceScape - Speaker Selection",
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
    if (input != null) {
      for (String speaker : speakers) {
        if (speaker.equals(input)) {
          speakerName = speaker;
          break;
        }
      }
    } else {
      JOptionPane.showMessageDialog(
          null,
          "No speaker selected. Please select a speaker.",
          "VoiceScape - No speaker selected!",
          JOptionPane.ERROR_MESSAGE);
      showSpeakerSelectionPrompt();
    }
  }

  @Subscribe
  public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked) {
    if (messageThread != null
        && (menuOptionClicked.getMenuOption().equals("Mute")
            || menuOptionClicked.getMenuOption().equals("Unmute"))
        && menuOptionClicked.getMenuTarget().contains(">")
        && menuOptionClicked.getMenuTarget().contains("<")) {
      String command = menuOptionClicked.getMenuOption().equals("Mute") ? "mute " : "unmute ";
      messageThread.out.println(
          command + menuOptionClicked.getMenuTarget().split(">")[1].split("<")[0]);
      client.addChatMessage(
          ChatMessageType.GAMEMESSAGE,
          "",
          menuOptionClicked.getMenuOption().equals("Mute")
              ? "Muted " + menuOptionClicked.getMenuTarget().split(">")[1].split("<")[0]
              : "Unmuted " + menuOptionClicked.getMenuTarget().split(">")[1].split("<")[0],
          "");
    }
  }

  @Subscribe
  public void onGameStateChanged(final GameStateChanged gameStateChanged) {
    new Timer()
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                if ((gameStateChanged.getGameState() == GameState.LOGGED_IN
                        || gameStateChanged.getGameState() == GameState.LOADING
                        || gameStateChanged.getGameState() == GameState.HOPPING)
                    && (voiceEngine == null || messageThread == null)) {
                  if (!config.useCustomServer()) {
                    runPluginThreads(mainServerIP);
                  } else {
                    runPluginThreads(config.customServerIP());
                  }
                } else if ((gameStateChanged.getGameState() != GameState.LOGGED_IN
                        && gameStateChanged.getGameState() != GameState.LOADING
                        && gameStateChanged.getGameState() != GameState.HOPPING)
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
    registeredPlayers.clear();
    indicatedPlayers.forEach(player -> player.setOverheadText(""));
    indicatedPlayers.clear();
    if (voiceEngine != null || messageThread != null) {
      shutdownAll();
    }
  }

  @Subscribe
  public void onConfigChanged(final ConfigChanged configChanged) {
    if (configChanged.getKey().equals("usecustomserver")) {
      if (voiceEngine != null || messageThread != null) {
        isRunning = false;
        shutdownAll();
      }
      if (!config.useCustomServer()) {
        int option =
            JOptionPane.showConfirmDialog(
                null,
                "Do you want to connect to " + mainServerIP + " instead?",
                "VoiceScape",
                JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
          runPluginThreads(mainServerIP);
        }
      } else if (config.useCustomServer()) {
        int option =
            JOptionPane.showConfirmDialog(
                null,
                "Using a custom server can cause a security risk.\nOnly use a custom server if you trust the server owner.\n\n"
                    + "Your microphone data is sent to the server and might be recorded by the server owner.\n"
                    + "Your IP address is sent to the server and might be used to identify you.\n\n"
                    + "Do you still want to use a custom server?",
                "VoiceScape - Custom Server Warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (option == JOptionPane.YES_OPTION) {
          runPluginThreads(config.customServerIP());
        }
      }
    } else if (configChanged.getKey().equals("volume")) {
      if (voiceEngine != null) {
        voiceEngine.voiceReceiverThread.updateSettings();
      }
    } else if (configChanged.getKey().equals("muteself")) {
      if (config.muteSelf() && voiceEngine != null) {
        new Timer()
            .schedule(
                new TimerTask() {
                  @Override
                  public void run() {
                    voiceEngine.sendEmpty = true;
                  }
                },
                1000);
      }
    } else if (configChanged.getKey().equals("performancemode")) {
      if (config.performanceMode()) {
        JOptionPane.showMessageDialog(
            null,
            "Performance mode will reduce the quality of the voice chat and the amount of clients you can talk with to reduce CPU and network usage.\nThis is recommended for low end computers and/or slow internet connections.",
            "VoiceScape - Performance Mode",
            JOptionPane.WARNING_MESSAGE);
      }
      if (voiceEngine != null && voiceEngine.voiceReceiverThread != null && messageThread != null) {
        isRunning = false;
        shutdownAll();
        JOptionPane.showMessageDialog(
            null,
            "You will need to re-enable the plugin for the changes to take effect.",
            "VoiceScape - Performance Mode",
            JOptionPane.INFORMATION_MESSAGE);
      }
    } else if (configChanged.getKey().equals("showownindicator")) {
      indicatedPlayers.forEach(
          player -> {
            if (player != null
                && player.getName() != null
                && player.getName().equals(client.getLocalPlayer().getName())) {
              player.setOverheadText("");
            }
          });
    }
  }

  private void runPluginThreads(String ip) {
    new Thread(
            () -> {
              voiceEngine = new VoiceEngine(ip, 24444, config);
              voiceEngine.completableFuture.whenCompleteAsync(
                  (aBoolean, throwable) -> {
                    if (aBoolean) {
                      messageThread = new MessageThread(ip, 23333, client, config, gson);
                      voiceEngine.messageThread = messageThread;
                    }
                  });
            })
        .start();
  }

  public void shutdownAll() {
    registeredPlayers.clear();
    indicatedPlayers.forEach(player -> player.setOverheadText(""));
    indicatedPlayers.clear();
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
