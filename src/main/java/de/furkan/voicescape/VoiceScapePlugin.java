package de.furkan.voicescape;

import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import javax.swing.*;

@Slf4j
@PluginDescriptor(name = "VoiceScape")
public class VoiceScapePlugin extends Plugin {

  private static final VoiceScapePlugin VOICE_SCAPE_PLUGIN_INSTANCE = new VoiceScapePlugin();
  private final String mainServerIP = "127.0.0.1";
  private VoiceEngine voiceEngine;
  private MessageThread messageThread;
  @Inject private Client client;
  @com.google.inject.Inject private Gson gson;
  @Inject private VoiceScapeConfig config;

  public static VoiceScapePlugin getInstance() {
    return VOICE_SCAPE_PLUGIN_INSTANCE;
  }

  @Override
  protected void startUp() throws Exception {
    if (config.connected()) {
      runThreads(mainServerIP);
    } else if (config.useCustomServer()) {
      runThreads(config.customServerIP());
    }
  }

  @Override
  protected void shutDown() throws Exception {
    if (voiceEngine != null || messageThread != null) {
      shutdownAll();
    }
  }

  @Subscribe
  public void onConfigChanged(ConfigChanged configChanged) {

    if (client.getGameState() != GameState.LOGGED_IN) {
      JOptionPane.showMessageDialog(
          null, "Please log in first!", "Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    if (configChanged.getKey().equals("connected")) {

      if (config.useCustomServer()) {
        JOptionPane.showMessageDialog(
            null,
            "Please disconnect from custom server first!",
            "Error",
            JOptionPane.ERROR_MESSAGE);
        return;
      }

      if (!config.connected() && voiceEngine != null && messageThread != null) {
        shutdownAll();
      } else if (config.connected() && voiceEngine == null && messageThread == null) {
        runThreads(mainServerIP);
      }

    } else if (configChanged.getKey().equals("usecustomserver")) {

      if (config.connected()) {
        JOptionPane.showMessageDialog(
            null, "Please disconnect first!", "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }

      if (!config.useCustomServer() && voiceEngine != null && messageThread != null) {
        shutdownAll();
      } else if (config.useCustomServer() && voiceEngine == null && messageThread == null) {
        runThreads(config.customServerIP());
      }

    } else if (configChanged.getKey().equals("volume")) {
      if (voiceEngine != null) {
        voiceEngine.voiceReceiverThread.updateSettings();
      }
    }
  }

  private void runThreads(String ip) {
    voiceEngine = new VoiceEngine(ip, 24444, config);
    messageThread = new MessageThread(ip, 25555, client, config, gson);
  }

  private void shutdownAll() {
    messageThread.out.println("disconnect");
    voiceEngine.stopEngine();
    messageThread.stopMessageThread();
    voiceEngine = null;
    messageThread = null;
    JOptionPane.showMessageDialog(
        null, "Disconnected from server!", "Disconnected", JOptionPane.INFORMATION_MESSAGE);
  }

  @Provides
  VoiceScapeConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(VoiceScapeConfig.class);
  }
}
