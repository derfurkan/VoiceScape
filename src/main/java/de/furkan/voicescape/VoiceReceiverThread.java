package de.furkan.voicescape;

import javax.sound.sampled.*;
import java.io.DataInputStream;
import java.net.Socket;

public class VoiceReceiverThread implements Runnable {

    /*Helper Class*/
    final Socket connection;
    final Thread thread;
    DataInputStream soundIn;
    SourceDataLine inSpeaker;
    private final VoiceScapeConfig config;

    public VoiceReceiverThread(Socket conn, VoiceScapeConfig voiceScapeConfig) {
        this.config = voiceScapeConfig;
        this.thread = new Thread(this, "VoiceReceiverThread");
        connection = conn;
        thread.start();
    }

    public void run() {
        try {
            soundIn = new DataInputStream(connection.getInputStream());
            AudioFormat af = new AudioFormat(16000, 8, 2, true, true);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);
            inSpeaker = (SourceDataLine) AudioSystem.getLine(info);
            inSpeaker.open(af);
            int bytesRead = 0;
            byte[] inSound = new byte[4096];
            updateSettings();
            while (bytesRead != -1) {
                bytesRead = soundIn.read(inSound);
                if (bytesRead >= 0) {
                    inSpeaker.write(inSound, 0, bytesRead);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateSettings() {
        if (inSpeaker.isRunning())
            inSpeaker.stop();

        FloatControl volume = (FloatControl) inSpeaker.getControl(FloatControl.Type.MASTER_GAIN);

        int vol = config.volume();
        float min = volume.getMinimum();
        float max = volume.getMaximum();

        float result = (vol * (max - min) / 100) + min;
        VoiceScape.getInstance().getLog().debug(String.valueOf(result));
        volume.setValue(result);
        inSpeaker.start();

    }



    public void stop() {
        try {
            inSpeaker.stop();
            inSpeaker.close();
            soundIn.close();
            connection.close();
            thread.interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
