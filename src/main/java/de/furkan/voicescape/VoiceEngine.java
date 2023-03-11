package de.furkan.voicescape;

import de.furkan.voicescape.jsocksSocksProxy.Socks5DatagramSocket;
import net.runelite.api.Client;

import javax.sound.sampled.*;
import javax.swing.*;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;

public class VoiceEngine implements Runnable {
    private final VoiceScapeConfig voiceScapeConfig;
    private final Socks5DatagramSocket connection;
    private final Client client;
    public Thread thread;
    public TargetDataLine microphone;

    public VoiceEngine(Socks5DatagramSocket connection, VoiceScapeConfig voiceScapeConfig, Client client) {
        this.client = client;
        this.connection = connection;
        this.voiceScapeConfig = voiceScapeConfig;

        try {
            this.thread = new Thread(this, "VoiceEngine");
            AudioFormat audioFormat = new AudioFormat(44100, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

            Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
            for (Mixer.Info info1 : mixerInfo) {
                Mixer mixer = AudioSystem.getMixer(info1);
                if (VoiceScapePlugin.getInstance().microphoneName != null && mixer.getMixerInfo().getName().startsWith(VoiceScapePlugin.getInstance().microphoneName)) {
                    info = (DataLine.Info) mixer.getLine(info);
                    break;
                }
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            VoiceScapePlugin.overlay.currentLine = "Opening microphone";
            microphone.open(audioFormat);
            microphone.start();
            VoiceScapePlugin.getInstance().voiceReceiver =
                    new VoiceReceiverThread(connection, voiceScapeConfig);
            this.thread.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
            VoiceScapePlugin.getInstance().shutdownAll("Could not open microphone. Please check your microphone.");
            SwingUtilities.invokeLater(
                    new Runnable() {
                        public void run() {
                            JOptionPane.showMessageDialog(
                                    null,
                                    "Could not open microphone. Please check your microphone.",
                                    "VoiceScape - Error",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
            VoiceScapePlugin.getInstance().shutdownAll(e.getMessage());
            thread.interrupt();
        }
    }

    static void connectionLostMessage(Client client, VoiceScapeConfig voiceScapeConfig) {
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run() {
                        int option =
                                JOptionPane.showConfirmDialog(
                                        null,
                                        "Connection to the voice server has been lost.\nHere are a few reasons why this could happen:\n- The server is not online\n- The server has rejected your request\n- You are using a proxy or VPN\n- You firewall/antivirus blocks the connection to the server\n\n"
                                                + "Do you want to reconnect?",
                                        "VoiceScape - Error",
                                        JOptionPane.YES_NO_OPTION,
                                        JOptionPane.ERROR_MESSAGE);

                        if (option == JOptionPane.YES_OPTION) {
                            VoiceScapePlugin.getInstance().shutdownAll("Reconnecting");
                            VoiceScapePlugin.getInstance().runPluginThreads(client, voiceScapeConfig);
                        }
                    }
                });
    }

    @Override
    public void run() {

        try {
            int bytesRead = 0;
            byte[] soundData = new byte[8192];
            while (bytesRead != -1 && VoiceScapePlugin.isRunning) {
                bytesRead = microphone.read(soundData, 0, 8192);
                if (bytesRead >= 0
                        && !voiceScapeConfig.muteSelf()
                        && VoiceScapePlugin.getInstance().sendMicrophoneData) {
                    if (voiceScapeConfig.pushToTalk() && !VoiceScapePlugin.canSpeak) {
                        VoiceScapePlugin.isTalking = false;
                        continue;
                    }
                    String uuid = VoiceScapePlugin.uuidString + "" + System.currentTimeMillis();

                    byte[] uuidBytes = uuid.getBytes();
                    byte[] newSoundData = new byte[soundData.length + uuidBytes.length];
                    System.arraycopy(uuidBytes, 0, newSoundData, 0, uuidBytes.length);
                    System.arraycopy(soundData, 0, newSoundData, uuidBytes.length, soundData.length);
                    soundData = newSoundData;


                    if (soundData.length > 65507) {
                        byte[] newSoundData2 = new byte[65507];
                        System.arraycopy(soundData, 0, newSoundData2, 0, 65507);
                        soundData = newSoundData2;
                    }


                    DatagramPacket packet = new DatagramPacket(soundData, soundData.length, new InetSocketAddress(voiceScapeConfig.customServerIP(), 24444));
                    if (voiceScapeConfig.useProxy()) {
                        connection.send(packet);
                    } else {
                        connection.socket.send(packet);
                    }
                    VoiceScapePlugin.isTalking = true;
                } else {
                    VoiceScapePlugin.isTalking = false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e.getMessage().startsWith("Socket") && VoiceScapePlugin.isRunning) {
                connectionLostMessage(client, voiceScapeConfig);
            }
            VoiceScapePlugin.getInstance().shutdownAll(e.getMessage());
        }
    }

    public void stopEngine() {
        try {
            connection.close();
            microphone.close();
            microphone.stop();
            thread.interrupt();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
