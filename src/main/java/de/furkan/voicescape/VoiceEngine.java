package de.furkan.voicescape;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class VoiceEngine implements Runnable {
    public VoiceReceiverThread voiceReceiverThread;
    private Socket connection;
    private TargetDataLine microphone;
    private Thread thread;
    private final VoiceScapeConfig voiceScapeConfig;

    public VoiceEngine(String serverIP, int serverPort, VoiceScapeConfig voiceScapeConfig) {
        this.voiceScapeConfig = voiceScapeConfig;
        try {
            this.thread = new Thread(this, "VoiceEngine");
            connection = new Socket(serverIP, serverPort);
            this.thread.start();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Could not connect to the server. Please try again later.");
            stopEngine();
        }
    }

    @Override
    public void run() {
        try {
            AudioFormat audioFormat = new AudioFormat(16000, 8, 2, true, true);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(audioFormat);
            microphone.start();
            OutputStream dos = connection.getOutputStream();
            int bytesRead = 0;
            byte[] soundData = new byte[4096];
            voiceReceiverThread = new VoiceReceiverThread(connection,voiceScapeConfig);
            while (bytesRead != -1) {
                bytesRead = microphone.read(soundData, 0, 4096);
                if (bytesRead >= 0) {
                    dos.write(soundData, 0, bytesRead);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopEngine() {
        try {
            if(connection == null)
                connection.close();

            microphone.stop();
            microphone.close();
            voiceReceiverThread.stop();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
