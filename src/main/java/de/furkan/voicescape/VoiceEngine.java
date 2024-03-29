package de.furkan.voicescape;

import net.runelite.api.GameState;
import net.runelite.api.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.*;
import java.util.Objects;

public class VoiceEngine {

    private final VoiceScapePlugin voiceScapePlugin;
    public Jedis jedisSub, jedisPub;
    TargetDataLine microphone;
    Thread microphoneCaptureThread;
    Thread subscribeThread;
    private JedisPubSub jedisPubSub;

    public VoiceEngine(VoiceScapePlugin voiceScapePlugin) {
        this.voiceScapePlugin = voiceScapePlugin;
    }

    AudioFormat getAudioFormat() {
        return new AudioFormat(44100.0f, 16, 1, true, false);
    }


    public void listenToChannel() {

        if (subscribeThread != null)
            subscribeThread.interrupt();

        if (jedisSub == null)
            return;

        if (jedisPubSub.isSubscribed())
            jedisPubSub.unsubscribe();

        subscribeThread = new Thread(() -> {
            if (voiceScapePlugin.client.getGameState() != GameState.LOGGED_IN)
                return;
            try {
                jedisSub.subscribe(jedisPubSub, String.valueOf(voiceScapePlugin.client.getWorld()));
            } catch (Exception e) {
                if (subscribeThread != null)
                    subscribeThread.interrupt();
            }
        });

        subscribeThread.start();

    }

    public void openConnection() {

        String ipAndPort = "";
        String redisPassword = voiceScapePlugin.config.customServerPassword();
        String redisUsername = voiceScapePlugin.config.customServerUsername();
        if (voiceScapePlugin.config.serverType() == VoiceScapeConfig.SERVER_TYPE.DEFAULT) {
            ipAndPort = "containers-us-west-176.railway.app:7525";
            redisUsername = "VoiceScapeUser";
            redisPassword = "HYpKxaZs6RbrH71MQfJwzj";
        } else if (voiceScapePlugin.config.serverType() == VoiceScapeConfig.SERVER_TYPE.CUSTOM) {
            ipAndPort = voiceScapePlugin.config.customServerIPAndPort();
        } else if (voiceScapePlugin.config.serverType() == VoiceScapeConfig.SERVER_TYPE.NONE) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Please select a server type in the configuration!", "VoiceScape - Server type error", JOptionPane.ERROR_MESSAGE);
            });
            return;
        }


        if (!(ipAndPort.contains(":") && ipAndPort.split(":")[1].matches("[0-9]+"))) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "The server ip and port is not in the correct format! It should be \"STRING:NUMBER\". Example: \"localhost:6379\"\n\nTo Reconnect re-enable the plugin.", "VoiceScape - Format error", JOptionPane.ERROR_MESSAGE);
            });
            return;
        }

        String redisHost = ipAndPort.split(":")[0];
        int redisPort = Integer.parseInt(ipAndPort.split(":")[1]);

        try {
            RedisPool jedisPool;
            if (redisPassword.isEmpty() && redisUsername.isEmpty()) {
                jedisPool = new RedisPool(2, redisHost, redisPort);
            } else {
                jedisPool = new RedisPool(2, redisHost, redisPort, redisUsername, redisPassword);
            }

            jedisSub = jedisPool.getResource();
            jedisPub = jedisPool.getResource();
            voiceScapePlugin.menuManager.addPlayerMenuItem(" Mute");
            voiceScapePlugin.menuManager.addPlayerMenuItem(" Un-mute");

            if (jedisPubSub != null)
                jedisPubSub.unsubscribe();

            jedisPubSub = new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {

                    if (voiceScapePlugin.client.getGameState() != GameState.LOGGED_IN)
                        return;
                    if (channel.equals(String.valueOf(voiceScapePlugin.client.getWorld())))
                        voiceScapePlugin.onRawMessageReceived(message);
                }
            };

            if (voiceScapePlugin.config.loopBack())
                voiceScapePlugin.registeredPlayers.add(voiceScapePlugin.hashWithSha256(voiceScapePlugin.client.getLocalPlayer().getName()));


            listenToChannel();
            startMicrophoneCapture();
            requestRegistration();

        } catch (Exception e) {
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Could not connect to VoiceScape servers!\nPlease check your configuration.\n\n" + e.getMessage() + "\n\nTo Reconnect re-enable the plugin.", "VoiceScape - Connection error", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    public void sendRawMessage(String message) {
        if (jedisPubSub == null)
            return;
        if (voiceScapePlugin.client.getGameState() != GameState.LOGGED_IN)
            return;
        try {
            jedisPub.publish(String.valueOf(voiceScapePlugin.client.getWorld()), message);
        } catch (Exception ignore) {

        }
    }

    public void requestDeletion() {
        sendRawMessage("delete#" + voiceScapePlugin.hashWithSha256(voiceScapePlugin.client.getLocalPlayer().getName()));
    }

    public void requestRegistration() {
        sendRawMessage("register#" + voiceScapePlugin.hashWithSha256(voiceScapePlugin.client.getLocalPlayer().getName()));
    }

    public void close() {
        voiceScapePlugin.menuManager.removePlayerMenuItem(" Mute");
        voiceScapePlugin.menuManager.removePlayerMenuItem(" Un-mute");
        requestDeletion();
        stopMicrophoneCapture();

        if (jedisPubSub != null) {
            if (jedisPubSub.isSubscribed())
                jedisPubSub.unsubscribe();
            jedisPubSub = null;
        }

        if (jedisSub != null) {
            jedisSub.disconnect();
            jedisSub.close();
            jedisSub = null;
        }

        if (subscribeThread != null) {
            subscribeThread.interrupt();
            subscribeThread = null;
        }

        if (jedisPub != null) {
            jedisPub.close();
            jedisPub = null;
        }
    }


    public void sendVoicePacket(VoicePacket voicePacket) {
        String data = voiceScapePlugin.gson.toJson(voicePacket);
        sendRawMessage(data);
    }

    public VoicePacket buildVoicePacket(byte[] voiceData) {
        return new VoicePacket(voiceData, voiceScapePlugin.hashWithSha256(voiceScapePlugin.client.getLocalPlayer().getName()));
    }

    public void startMicrophoneCapture() {
        if (microphoneCaptureThread != null) {
            microphoneCaptureThread.interrupt();
            microphoneCaptureThread = null;
        }

        microphoneCaptureThread = new Thread(() -> {
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, getAudioFormat());
                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(getAudioFormat());
                microphone.start();

                int bufferSize;
                if (voiceScapePlugin.config.lowCaptureBuffer())
                    bufferSize = 20_000;
                else
                    bufferSize = (int) getAudioFormat().getSampleRate() * getAudioFormat().getFrameSize();

                byte[] buffer = new byte[bufferSize];

                while (microphoneCaptureThread != null && !microphoneCaptureThread.isInterrupted()) {

                    if (voiceScapePlugin.client.getGameState() != GameState.LOGGED_IN)
                        continue;

                    if (voiceScapePlugin.client.getPlayers().isEmpty()) {
                        continue;
                    }

                    boolean isValid = false;

                    for (Player player : voiceScapePlugin.client.getPlayers()) {
                        if (player == null)
                            continue;
                        if (Objects.equals(player.getName(), voiceScapePlugin.client.getLocalPlayer().getName()) && !voiceScapePlugin.config.loopBack())
                            continue;
                        if (voiceScapePlugin.registeredPlayers.contains(voiceScapePlugin.hashWithSha256(player.getName()))) {

                            isValid = true;
                            break;
                        }
                    }
                    if (!isValid) {
                        continue;
                    }

                    if (voiceScapePlugin.config.pushToTalk() && !voiceScapePlugin.canSpeak) {
                        continue;
                    }

                    if (voiceScapePlugin.config.muteSelf()) {
                        continue;
                    }

                    int count = microphone.read(buffer, 0, buffer.length);

                    if (count > 0) {
                        byte[] tempBuffer = new byte[count];
                        System.arraycopy(buffer, 0, tempBuffer, 0, count);
                        sendVoicePacket(buildVoicePacket(tempBuffer));
                    }

                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        microphoneCaptureThread.start();
    }

    public void stopMicrophoneCapture() {
        if (microphone != null) {
            microphone.stop();
            microphone.close();
            microphone = null;
        }
        if (microphoneCaptureThread != null) {
            microphoneCaptureThread.interrupt();
            microphoneCaptureThread = null;
        }
    }

}