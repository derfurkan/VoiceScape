package de.furkan.voicescape;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.inject.Provides;
import de.furkan.voicescape.jsocksSocksProxy.SocksProxyBase;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import javax.inject.Inject;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import javax.swing.*;
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Timer;
import java.util.*;

@Slf4j
@PluginDescriptor(name = "VoiceScape")
public class VoiceScapePlugin extends Plugin {

    private static final VoiceScapePlugin VOICE_SCAPE_PLUGIN_INSTANCE = new VoiceScapePlugin();
    public static boolean isRunning;
    public static ArrayList<String> registeredPlayers = Lists.newArrayList();
    public static ArrayList<String> mutedPlayers = Lists.newArrayList();
    public static boolean canSpeak = false;
    public static VoiceScapeOverlay overlay;

    public static VoiceScapeDebugOverlay overlayDebug;
    public static String uuidString = generateUUID();
    public static long lastUpdateMessage = 0;
    public static long lastVoicePacket = 0;
    public static boolean isTalking = false;
    public static boolean alreadyShutDown = false;
    private final HashMap<String, String> nameHashes = new HashMap<>();
    public MessageThread messageThread;
    public String microphoneName;
    public String speakerName;
    public VoiceEngine voiceEngine;
    public VoiceReceiverThread voiceReceiver;
    public boolean sendMicrophoneData = false;
    @Inject
    private Client client;
    @Inject
    private Gson gson;
    @Inject
    private VoiceScapeConfig config;
    private final HotkeyListener hotkeyListener =
            new HotkeyListener(() -> this.config.pushToTalkBind()) {
                @Override
                public void keyPressed(KeyEvent e) {
                    canSpeak = true;
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    canSpeak = false;
                }
            };
    @Inject
    private MenuManager menuManager;
    @Inject
    private KeyManager keyManager;
    @Inject
    private OverlayManager overlayManager;

    public static VoiceScapePlugin getInstance() {
        return VOICE_SCAPE_PLUGIN_INSTANCE;
    }

    private static String generateUUID() {
        char[] supportedCharacters = "1234567890".toCharArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(supportedCharacters[new Random().nextInt(supportedCharacters.length)]);
        }
        return sb.toString();
    }

    @Override
    protected void startUp() throws Exception {
        overlay = new VoiceScapeOverlay(client, config);
        overlayDebug = new VoiceScapeDebugOverlay(client, config);
        overlayManager.add(overlay);
        overlayManager.add(overlayDebug);
        if (AudioSystem.getMixerInfo().length == 0) {
            JOptionPane.showMessageDialog(
                    null,
                    "No audio devices found. Please check your audio settings.",
                    "VoiceScape - No audio devices found!",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        keyManager.registerKeyListener(hotkeyListener);

        new Timer()
                .schedule(
                        new TimerTask() {
                            @Override
                            public void run() {
                                SwingUtilities.invokeLater(
                                        () -> {

                                            JOptionPane.showMessageDialog(
                                                    null,
                                                    "<html><h1>Disclaimer<h1/><html/> \n" +
                                                            "By using this in-game voice chat plugin to connect to servers hosted by other players, you\n" +
                                                            "acknowledge that joining a server run by someone you do not know carries inherent risks. The\n" +
                                                            "plugin's developers cannot guarantee complete security, and are not responsible for any\n" +
                                                            "damages or loss of data that may occur.\n",
                                                    "VoiceScape - Disclaimer",
                                                    JOptionPane.WARNING_MESSAGE);

                                            showMicrophoneSelectionPrompt();
                                            showSpeakerSelectionPrompt();

                                            menuManager.addPlayerMenuItem("Mute");
                                            menuManager.addPlayerMenuItem("Un-mute");

                                            if (client.getGameState() == GameState.LOGGED_IN) {
                                                shutdownAll("Client logged in");
                                                if (config.useCustomServer()) runPluginThreads(client, config);
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
                && menuOptionClicked.getMenuTarget().contains(">")
                && menuOptionClicked.getMenuTarget().contains("<")) {
            String playerName = menuOptionClicked.getMenuTarget().split(">")[1].split("<")[0];
            if (menuOptionClicked.getMenuOption().equals("Mute") && !mutedPlayers.contains(hashWithSha256(playerName))) {
                mutedPlayers.add(hashWithSha256(playerName));
            } else if (menuOptionClicked.getMenuOption().equals("Un-mute")) {
                mutedPlayers.remove(hashWithSha256(playerName));
            }
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
                                        && (voiceEngine == null && messageThread == null)) {
                                    if (config.useCustomServer()) {
                                        runPluginThreads(client, config);
                                    }
                                } else if ((gameStateChanged.getGameState() != GameState.LOGGED_IN
                                        && gameStateChanged.getGameState() != GameState.LOADING
                                        && gameStateChanged.getGameState() != GameState.HOPPING)
                                        && (voiceEngine != null || messageThread != null)) {
                                    shutdownAll("Game state changed.");
                                }
                            }
                        },
                        3000);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(overlay);
        overlayManager.remove(overlayDebug);
        menuManager.removePlayerMenuItem("Mute");
        menuManager.removePlayerMenuItem("Un-mute");
        isRunning = false;
        registeredPlayers.clear();
        if (voiceEngine != null || messageThread != null) {
            shutdownAll("Plugin shutdown.");
        }
    }

    @Subscribe
    public void onConfigChanged(final ConfigChanged configChanged) {

        if (configChanged.getKey().equals("usecustomserver")) {
            if (config.useCustomServer()) {
                int option =
                        JOptionPane.showConfirmDialog(
                                null,
                                "Only connect to where you trust the server owner.\n"
                                        + "Your microphone data is sent to the server and might be recorded by the server owner.\n"
                                        + "Your IP address is sent to the server and might be used to identify you.\n\n"
                                        + "Do you want to connect?",
                                "VoiceScape - Warning",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE);
                if (option == JOptionPane.YES_OPTION) {
                    runPluginThreads(client, config);
                }
            } else {
                shutdownAll("Custom server disabled.");
            }
        } else if (configChanged.getKey().equals("volume")) {

            if (voiceEngine != null) {
                voiceReceiver.updateSettings();
            }
        } else if (configChanged.getKey().equals("useproxy")) {
            if (isRunning) {
                SocksProxyBase.setDefaultProxy(null);
                JOptionPane.showMessageDialog(
                        null,
                        "To apply this setting you will be re-connected.",
                        "VoiceScape - Proxy settings",
                        JOptionPane.INFORMATION_MESSAGE);
                shutdownAll("Proxy settings changed.");
                runPluginThreads(client, config);
            }
        } else if (configChanged.getKey().equals("defaultserver")) {
            shutdownAll("Default server changed.");
            if (config.defaultServers() != VoiceScapeConfig.DEFAULT_SERVERS.CUSTOM) {
                JOptionPane.showMessageDialog(
                        null,
                        "Default servers are not trusted these are just servers provided by the community.\nThey might be modified or offline so use them at your own risk!",
                        "VoiceScape - Warning",
                        JOptionPane.WARNING_MESSAGE);
            }
            if (config.useCustomServer()) {
                runPluginThreads(client, config);
            }
        } else if (configChanged.getKey().equals("muteself")) {
            if (voiceEngine != null) {
                if (config.muteSelf()) {
                    voiceEngine.microphone.stop();
                } else {
                    voiceEngine.microphone.start();
                }
            }
        } else if (configChanged.getKey().equals("performancemode")) {
            if (config.performanceMode()) {
                JOptionPane.showMessageDialog(
                        null,
                        "Performance mode will reduce the amount of clients you can talk with to reduce CPU and network usage.\nThis is recommended for low end computers and/or slow internet connections.",
                        "VoiceScape - Performance Mode",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    public void runPluginThreads(Client client, VoiceScapeConfig config) {
        if (isRunning) {
            return;
        }
        isRunning = true;
        alreadyShutDown = false;
        overlayDebug.lastVoicePacket = "Nothing received";
        new Thread(
                () -> {
                    if ((client.getGameState() == GameState.LOGGED_IN
                            || client.getGameState() == GameState.LOADING
                            || client.getGameState() == GameState.HOPPING)) {
                        String ip = "127.0.0.1";
                        if (config.defaultServers() == VoiceScapeConfig.DEFAULT_SERVERS.CUSTOM) {
                            ip = config.customServerIP();
                        } else if (config.defaultServers() == VoiceScapeConfig.DEFAULT_SERVERS.VERAC) {
                            ip = "verac.pro";
                        } else if (config.defaultServers() == VoiceScapeConfig.DEFAULT_SERVERS.THEBEERKEG) {
                            ip = "thebeerkeg.net";
                        } else if (config.defaultServers() == VoiceScapeConfig.DEFAULT_SERVERS.VOICE_NGA) {
                            ip = "voicescape.nga.gg";
                        }
                        messageThread = new MessageThread(ip, 23333, client, config, gson);
                    } else {
                        SwingUtilities.invokeLater(
                                () -> {
                                    JOptionPane.showMessageDialog(
                                            null,
                                            "You need to be logged in to connect to a server.",
                                            "VoiceScape - Error",
                                            JOptionPane.ERROR_MESSAGE);
                                });
                    }
                })
                .start();
    }

    public void shutdownAll(String reason) {
        alreadyShutDown = true;
        overlayDebug.lastVoicePacket = "Not connected";
        //  System.out.println("Shutting down VoiceScape plugin threads for reason: " + reason);
        isRunning = false;
        VoiceScapePlugin.lastVoicePacket = 0;
        VoiceScapePlugin.lastUpdateMessage = 0;
        overlayDebug.lastVoiceDiff = 0;
        SocksProxyBase.setDefaultProxy(null);
        overlay.currentLine = "";
        nameHashes.clear();
        registeredPlayers.clear();
        if (messageThread != null) {
            if (messageThread.out != null) messageThread.out.println("disconnect");
            messageThread.stop();
        }
        if (voiceReceiver != null) {
            voiceReceiver.stopReceiver();
        }

        if (voiceEngine != null) {
            voiceEngine.stopEngine();
        }

        voiceEngine = null;
        messageThread = null;
    }

    public String hashWithSha256(final String base) {
        if (base == null) return "";
        if (nameHashes.containsKey(base)) return nameHashes.get(base);
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                final String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            nameHashes.put(base, hexString.toString());
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


    @Provides
    VoiceScapeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(VoiceScapeConfig.class);
    }
}
