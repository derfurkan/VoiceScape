package de.furkan.voicescape;

import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;

@Slf4j
@PluginDescriptor(name = "VoiceScape")
public class VoiceScapePlugin extends Plugin {

  private static final VoiceScapePlugin VOICE_SCAPE_PLUGIN_INSTANCE = new VoiceScapePlugin();
  private final String mainServerIP = "217.85.236.87";
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
    if(client.getGameState() == GameState.LOGGED_IN) {
      if(config.useCustomServer())
        runThreads(config.customServerIP());
      else
        runThreads(mainServerIP);
    }
  }

  @Subscribe
  public void onGameStateChanged(GameStateChanged gameStateChanged) {
    if (gameStateChanged.getGameState() == GameState.LOGGED_IN && (voiceEngine == null || messageThread == null)) {
        if (!config.useCustomServer()) {
          runThreads(mainServerIP);
        } else {
          runThreads(config.customServerIP());
        }
      }
    if (gameStateChanged.getGameState() != GameState.LOGGED_IN && (voiceEngine != null || messageThread != null)) {
        shutdownAll();
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

    if (configChanged.getKey().equals("usecustomserver")) {
      shutdownAll();

      if (!config.useCustomServer()) {
        runThreads(mainServerIP);
      } else if (config.useCustomServer()) {
        runThreads(config.customServerIP());
      }

    } else if (configChanged.getKey().equals("volume")) {
      if (voiceEngine != null) {
        voiceEngine.voiceReceiverThread.updateSettings();
      }
    }
  }

  private void runThreads(String ip) {
 new Thread(() -> {
   voiceEngine = new VoiceEngine(ip, 24444, config);
   voiceEngine.completableFuture.whenCompleteAsync((aBoolean, throwable) -> {
     if(aBoolean) {
       messageThread = new MessageThread(ip, 25555, client, config, gson);
     }
   });
    }).start();
  }

  private void shutdownAll() {

    voiceEngine.thread.interrupt();
    if(messageThread != null)
      messageThread.thread.interrupt();
    voiceEngine = null;
    messageThread = null;
  }

  @Provides
  VoiceScapeConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(VoiceScapeConfig.class);
  }
}
