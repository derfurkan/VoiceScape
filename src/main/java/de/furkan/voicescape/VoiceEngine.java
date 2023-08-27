package de.furkan.voicescape;

import net.runelite.api.GameState;
import net.runelite.api.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;

import javax.sound.sampled.*;
import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.util.List;

public class VoiceEngine {

    public Jedis jedisSub, jedisPub;
    private JedisPool jedisPool;

    private JedisPubSub jedisPubSub;

    private final VoiceScapePlugin voiceScapePlugin;

    public VoiceEngine(VoiceScapePlugin voiceScapePlugin) {
        this.voiceScapePlugin = voiceScapePlugin;
    }
    Clip currentClip;
    TargetDataLine microphone;
    Thread microphoneCaptureThread;

    Thread subscribeThread;


    AudioFormat getAudioFormat() {
        return new AudioFormat(44100.0f, 16, 1, true, true);
    }


    public void listenToChannel() {
        if(subscribeThread != null)
            subscribeThread.interrupt();

        if(jedisSub == null)
            return;

        if(jedisPubSub.isSubscribed())
            jedisPubSub.unsubscribe();

        subscribeThread =  new Thread(() -> {
            if(voiceScapePlugin.client.getGameState() != GameState.LOGGED_IN)
                return;
            try {
                jedisSub.subscribe(jedisPubSub, String.valueOf(voiceScapePlugin.client.getWorld()));
            } catch (Exception e) {
                subscribeThread.interrupt();
            }
        });
        subscribeThread.start();

    }

    public void openConnection()
    {
        String ipAndPort = "";
        String redisPassword = voiceScapePlugin.config.customServerPassword();
        String redisUsername = voiceScapePlugin.config.customServerUsername();
        if(voiceScapePlugin.config.serverType() == VoiceScapeConfig.SERVER_TYPE.DEFAULT) {
            ipAndPort = "redis-13771.c135.eu-central-1-1.ec2.cloud.redislabs.com:13771";
            redisUsername = "PlayerMarker-USER";
            redisPassword = "Vh2#SxV6KpQbnZ!dCNwB";
        } else if (voiceScapePlugin.config.serverType() == VoiceScapeConfig.SERVER_TYPE.CUSTOM) {
            ipAndPort = voiceScapePlugin.config.customServerIPAndPort();
        }

        if(!(ipAndPort.contains(":") && ipAndPort.split(":")[1].matches("[0-9]+"))) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "The server ip and port is not in the correct format! It should be \"STRING:NUMBER\". Example: \"localhost:6379\"\n\nTo Reconnect re-enable the plugin.", "VoiceScape - Format error", JOptionPane.ERROR_MESSAGE);
            });
            return;
        }

        String redisHost = ipAndPort.split(":")[0];
        int redisPort = Integer.parseInt(ipAndPort.split(":")[1]);


        if (redisPassword.isEmpty() && redisUsername.isEmpty()) {
            jedisPool = new JedisPool(redisHost, redisPort);
        } else {
            jedisPool = new JedisPool(redisHost, redisPort, redisUsername, redisPassword);
        }
        try{
            jedisSub = jedisPool.getResource();
            jedisPub = jedisPool.getResource();
            voiceScapePlugin.menuManager.addPlayerMenuItem(" Mute");
            voiceScapePlugin.menuManager.addPlayerMenuItem(" Un-mute");

            if(jedisPubSub != null)
                jedisPubSub.unsubscribe();

            jedisPubSub = new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {

                    if(voiceScapePlugin.client.getGameState() != GameState.LOGGED_IN)
                        return;
                    if(channel.equals(String.valueOf(voiceScapePlugin.client.getWorld())))
                        voiceScapePlugin.onRawMessageReceived(message);
                }
            };

            if(voiceScapePlugin.config.loopBack())
                voiceScapePlugin.registeredPlayers.add(voiceScapePlugin.hashWithSha256(voiceScapePlugin.client.getLocalPlayer().getName()));

            listenToChannel();
            startMicrophoneCapture();
            requestRegistration();

        } catch (Exception e) {
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Could not connect to VoiceScape servers!\nPlease check your configuration.\n\n"+ e.getMessage() +"\n\nTo Reconnect re-enable the plugin.", "VoiceScape - Connection error", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    public void sendRawMessage(String message) {
        if(jedisPubSub == null)
            return;
        if(voiceScapePlugin.client.getGameState() != GameState.LOGGED_IN)
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


    public void sendVoicePacket(VoicePacket voicePacket) {
        sendRawMessage(voiceScapePlugin.gson.toJson(voicePacket));
    }

    public VoicePacket buildVoicePacket(byte[] voiceData) {
        return new VoicePacket(voiceData, voiceScapePlugin.hashWithSha256(voiceScapePlugin.client.getLocalPlayer().getName()));
    }



    public void reconnect()
    {
        close();
        openConnection();
    }

    public void close()
    {
        voiceScapePlugin.menuManager.removePlayerMenuItem(" Mute");
        voiceScapePlugin.menuManager.removePlayerMenuItem(" Un-mute");
        requestDeletion();
        stopMicrophoneCapture();

        if(jedisPubSub != null)
            jedisPubSub.unsubscribe();

        if(jedisSub != null) {
            jedisSub.disconnect();
            jedisSub.close();
        }

        if(subscribeThread != null)
            subscribeThread.interrupt();

        if(jedisPub != null)
            jedisPub.close();

        jedisPool.close();
    }


    public void startMicrophoneCapture() {
        if(microphoneCaptureThread != null)
            microphoneCaptureThread.interrupt();

        microphoneCaptureThread = new Thread(() -> {
            try {
               DataLine.Info info = new DataLine.Info(TargetDataLine.class, getAudioFormat());
                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(getAudioFormat());
                microphone.start();

                int bufferSize = (int) getAudioFormat().getSampleRate() * getAudioFormat().getFrameSize();
                byte[] buffer = new byte[bufferSize];

                while (!microphoneCaptureThread.isInterrupted()) {

                    if(voiceScapePlugin.client.getGameState() != GameState.LOGGED_IN)
                        continue;
                    boolean isValid = false;

                    for (Player player : voiceScapePlugin.client.getPlayers()) {
                        if(player == null)
                            continue;
                        if(voiceScapePlugin.registeredPlayers.contains(voiceScapePlugin.hashWithSha256(player.getName()))) {
                            isValid = true;
                            break;
                        }
                    }
                    if(!isValid) {
                        continue;
                    }

                    if(voiceScapePlugin.config.pushToTalk() && !voiceScapePlugin.canSpeak) {
                        continue;
                    }

                    if(voiceScapePlugin.config.muteSelf()) {
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
        microphone.stop();
        microphone.close();
        if(microphoneCaptureThread != null)
            microphoneCaptureThread.interrupt();
    }


    public void playAudio(byte[] audioData, float volume) {
        try {
            currentClip = AudioSystem.getClip();
            currentClip.open(getAudioFormat(), audioData, 0, audioData.length);
            FloatControl volumeControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);

            volumeControl.setValue(20f * (float) Math.log10(volume));


            currentClip.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
