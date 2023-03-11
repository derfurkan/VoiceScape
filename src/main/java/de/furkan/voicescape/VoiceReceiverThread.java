package de.furkan.voicescape;

import de.furkan.voicescape.jsocksSocksProxy.Socks5DatagramSocket;
import org.apache.commons.lang3.math.NumberUtils;

import javax.sound.sampled.*;
import javax.swing.*;
import java.net.DatagramPacket;

public class VoiceReceiverThread implements Runnable {
    final Socks5DatagramSocket connection;
    final Thread thread;
    private final VoiceScapeConfig config;
    SourceDataLine inSpeaker;
    private boolean showInvalidPacketMessage = false;

    public VoiceReceiverThread(Socks5DatagramSocket connection, VoiceScapeConfig voiceScapeConfig) {
        this.config = voiceScapeConfig;
        this.thread = new Thread(this, "VoiceReceiverThread");
        this.connection = connection;
        this.thread.start();
    }

    public void run() {
        try {
            AudioFormat af = new AudioFormat(44100, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);

            Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
            for (Mixer.Info info1 : mixerInfo) {
                Mixer mixer = AudioSystem.getMixer(info1);
                if (VoiceScapePlugin.getInstance().speakerName != null && mixer.getMixerInfo().getName().startsWith(VoiceScapePlugin.getInstance().speakerName)) {
                    info = (DataLine.Info) mixer.getLine(info);
                    break;
                }
            }

            inSpeaker = (SourceDataLine) AudioSystem.getLine(info);
            inSpeaker.open(af);
            int bytesRead = 0;
            byte[] inSound = new byte[8192];
            updateSettings();
            while (bytesRead != -1 && VoiceScapePlugin.isRunning) {
                inSpeaker.stop();
                DatagramPacket packet = new DatagramPacket(inSound, inSound.length);

                if (config.useProxy()) {
                    connection.receive(packet);
                } else {
                    connection.socket.receive(packet);
                }

                String packetToString = new String(packet.getData());
                if (!NumberUtils.isDigits(packetToString.substring(0, 13))) {
                    VoiceScapePlugin.overlayDebug.lastVoicePacket = "Invalid packet";
                    if (!showInvalidPacketMessage) {
                        JOptionPane.showMessageDialog(
                                null,
                                "Invalid voice packet received. This might indicate that the Server is not up to date.",
                                "VoiceScape - Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                    showInvalidPacketMessage = true;
                    continue;
                }
                showInvalidPacketMessage = false;
                long diff = System.currentTimeMillis() - VoiceScapePlugin.lastVoicePacket;
                VoiceScapePlugin.lastVoicePacket = Long.parseLong(packetToString.substring(0, 13));

                VoiceScapePlugin.overlayDebug.lastVoicePacket = diff + " ms ago";

                byte[] croppedPacket = new byte[packet.getLength() - 13];
                System.arraycopy(packet.getData(), 13, croppedPacket, 0, packet.getLength() - 13);
                packet.setData(croppedPacket);
                packet.setLength(packet.getLength() - 13);

                bytesRead = packet.getLength();
                if (bytesRead >= 0) {
                    inSpeaker.start();
                    inSpeaker.write(packet.getData(), 0, packet.getLength());
                }
            }
            VoiceScapePlugin.getInstance().shutdownAll("VoiceReceiverThread closed");
        } catch (Exception e) {
            VoiceScapePlugin.getInstance().shutdownAll(e.getMessage());
        }
    }

    public void updateSettings() {
        inSpeaker.stop();
        FloatControl volume = (FloatControl) inSpeaker.getControl(FloatControl.Type.MASTER_GAIN);
        int vol = config.volume();
        float min = volume.getMinimum();
        float max = volume.getMaximum();
        float result = (vol * (max - min) / 100) + min;
        volume.setValue(result);
        inSpeaker.start();
    }

    public void stopReceiver() {
        try {
            if (connection != null) {
                connection.close();
            }
            if (inSpeaker != null) {
                inSpeaker.stop();
                inSpeaker.close();
            }
            thread.interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
