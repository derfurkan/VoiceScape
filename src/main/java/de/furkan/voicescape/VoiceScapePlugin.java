package de.furkan.voicescape;

import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
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
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@PluginDescriptor(name = "VoiceScape")
public class VoiceScapePlugin extends Plugin {

    private final HashMap<String, String> nameHashes = new HashMap<>();
    @Inject
    public Client client;
    @Inject
    public Gson gson;
    @Inject
    public VoiceScapeConfig config;
    @Inject
    OverlayManager overlayManager;

    @Inject
    KeyManager keyManager;

    public boolean canSpeak = false;

    private final HotkeyListener hotkeyListener =
            new HotkeyListener(() -> this.config.pushToTalkBind()) {
                @Override
                public void keyPressed(KeyEvent e) {
                    canSpeak = true;
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            canSpeak = false;
                        }
                    }, 1000);
                }
            };
    public VoiceScapeOverlay voiceScapeOverlay;
    public VoiceEngine voiceEngine;

    public List<String> registeredPlayers = new ArrayList<>();

    public List<String> mutedPlayers = new ArrayList<>();

    public List<String> unmutedPlayers = new ArrayList<>();

    @Inject
    public MenuManager menuManager;
    @Override
    protected void startUp() throws Exception {
        if (client.getGameState().equals(GameState.LOGGED_IN)) {
            initializePlugin();
        }
    }


    private void initializePlugin() {
        voiceEngine = new VoiceEngine(this);
        voiceEngine.openConnection();
        keyManager.registerKeyListener(hotkeyListener);
        overlayManager.add(voiceScapeOverlay = new VoiceScapeOverlay(this));
    }

    private void shutdownPlugin() {
        keyManager.unregisterKeyListener(hotkeyListener);
        overlayManager.remove(voiceScapeOverlay);
        if(voiceEngine != null) {
            voiceEngine.close();
            voiceEngine = null;
        }
        registeredPlayers.clear();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked) {
        if (menuOptionClicked.getMenuTarget().contains(">")
                && menuOptionClicked.getMenuTarget().contains("<")) {
            String playerName = menuOptionClicked.getMenuTarget().split(">")[1].split("<")[0];
            playerName = playerName.replaceAll(" ", " ");
            if (menuOptionClicked.getMenuOption().equals(" Mute") && !mutedPlayers.contains(hashWithSha256(playerName))) {
                mutedPlayers.add(hashWithSha256(playerName));
                unmutedPlayers.remove(hashWithSha256(playerName));
            } else if (menuOptionClicked.getMenuOption().equals(" Un-mute")) {
                mutedPlayers.remove(hashWithSha256(playerName));
                unmutedPlayers.add(hashWithSha256(playerName));
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(final GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    initializePlugin();
                }
            },3000);
        } else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
            shutdownPlugin();
        }
    }

    @Override
    protected void shutDown() throws Exception {
        shutdownPlugin();
    }

    @Subscribe
    public void onConfigChanged(final ConfigChanged configChanged) {
        if(configChanged.getKey().equals("loopback") && client.getGameState() == GameState.LOGGED_IN) {
            if(config.loopBack())
                registeredPlayers.add(hashWithSha256(client.getLocalPlayer().getName()));
            else
                registeredPlayers.remove(hashWithSha256(client.getLocalPlayer().getName()));
        }
    }

    public void onRawMessageReceived(String message) {
        //Using try catch to prevent plugin from crashing
        try {
            if (message.startsWith("delete#")) {
                registeredPlayers.remove(message.split("#")[1]);
                return;
            } else if (message.startsWith("register#")) {
                registeredPlayers.add(message.split("#")[1]);
                return;
            }

            VoicePacket voicePacket = gson.fromJson(message, VoicePacket.class);
            //Making sure voice packet is not corrupted
            if (voicePacket == null || voicePacket.audioData == null || voicePacket.audioData.length == 0) return;
            if (!config.loopBack() && voicePacket.senderNameHashed.equals(hashWithSha256(client.getLocalPlayer().getName()))) return;
            if (!registeredPlayers.contains(voicePacket.senderNameHashed))
                registeredPlayers.add(voicePacket.senderNameHashed);

            if(config.muteAll() && !unmutedPlayers.contains(voicePacket.senderNameHashed))
                return;

            if(mutedPlayers.contains(voicePacket.senderNameHashed))
                return;

            //Making sure player is in surrounding area
            boolean isInSurroundingArea = false;
            Player sender = null;
            for (Player player : client.getPlayers()) {
                String hashedName = hashWithSha256(player.getName());
                if (hashedName.equals(voicePacket.senderNameHashed)) {
                    sender = player;
                    isInSurroundingArea = true;
                    break;
                }
            }


            if (isInSurroundingArea) {

                //Calculate volume based on distance
                int distanceToSender = client.getLocalPlayer().getWorldLocation().distanceTo(sender.getWorldLocation());
                float volume = 1 - ((float) distanceToSender / config.minDistance()) - 0.005f;
                voiceEngine.playAudio(voicePacket.audioData, volume);

            }




        } catch (Exception e) {
            e.printStackTrace();
        }
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
