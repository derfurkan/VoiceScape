package de.furkan.voicescape;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.swing.*;

@Slf4j
@PluginDescriptor(
        name = "VoiceScape"
)
public class VoiceScapePlugin extends Plugin {

	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(VoiceScapePlugin.class);
		RuneLite.main(args);
	}

	private final String mainServerIP = "127.0.0.1";
    private final static VoiceScapePlugin VOICE_SCAPE_PLUGIN_INSTANCE = new VoiceScapePlugin();
    private VoiceEngine voiceEngine;
    private MessageThread messageThread;
    @Inject
    private Client client;
    @Inject
    private VoiceScapeConfig config;

	public Logger getLog() {
		return log;
	}

	public static VoiceScapePlugin getInstance() {
        return VOICE_SCAPE_PLUGIN_INSTANCE;
    }

    @Override
    protected void startUp() throws Exception {
		resetConfiguration();
    }

    @Override
    protected void shutDown() throws Exception {
		if (voiceEngine != null && messageThread != null) {
			shutdownAll();
		}
    }

	public boolean useCustomServer = false;
	public boolean connected = false;

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged) {
        if (configChanged.getKey().equals("connected")) {

			if(client.getGameState() != GameState.LOGGED_IN) {
				JOptionPane.showMessageDialog(null, "Please log in first!", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			if(config.useCustomServer()) {
				JOptionPane.showMessageDialog(null, "Please disconnect from custom server first!", "Error", JOptionPane.ERROR_MESSAGE);
				configChanged.setOldValue(configChanged.getOldValue());
				connected = !connected;
				return;
			}
			if (!config.connected() && voiceEngine != null && messageThread != null) {
				shutdownAll();
            } else if (config.connected() && voiceEngine == null && messageThread == null) {
				runThreads(mainServerIP);
            }
        } else if (configChanged.getKey().equals("usecustomserver")) {

			if(client.getGameState() != GameState.LOGGED_IN) {
				JOptionPane.showMessageDialog(null, "Please log in first!", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			if(config.connected()) {
				JOptionPane.showMessageDialog(null, "Please disconnect first!", "Error", JOptionPane.ERROR_MESSAGE);
				configChanged.setNewValue(configChanged.getOldValue());
				useCustomServer = !useCustomServer;
				return;
			}
			if(config.useCustomServer()) {
				runThreads(config.customServerIP());
			} else {
				shutdownAll();
			}
		}else if (configChanged.getKey().equals("volume")) {
			if (voiceEngine != null) {
				voiceEngine.voiceReceiverThread.updateSettings();
			}
		}
    }

	private void runThreads(String ip) {
		voiceEngine = new VoiceEngine(ip, 24444, config);
		messageThread = new MessageThread(ip, 25555, client, config);
	}

	private void shutdownAll() {
		messageThread.sendMessageToServer("disconnect");
		voiceEngine.stopEngine();
		messageThread.stopConnection();
		voiceEngine = null;
		messageThread = null;
	}


    @Provides
    VoiceScapeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(VoiceScapeConfig.class);
    }
}
