package de.furkan.voicescape;

import javax.sound.sampled.*;
import java.io.DataInputStream;
import java.net.Socket;

public class VoiceReceiverThread implements Runnable {
  final Socket connection;
  final Thread thread;
  private final VoiceScapeConfig config;
  DataInputStream soundIn;
  SourceDataLine inSpeaker;

  public VoiceReceiverThread(Socket connection, VoiceScapeConfig voiceScapeConfig) {
    this.config = voiceScapeConfig;
    this.thread = new Thread(this, "VoiceReceiverThread");
    this.connection = connection;
    this.thread.start();
  }

  public void run() {
    try {
      soundIn = new DataInputStream(connection.getInputStream());
      AudioFormat af = new AudioFormat(44100, 16, 2, true, true);
      DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);

      Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
      for (Mixer.Info info1 : mixerInfo) {
        Mixer mixer = AudioSystem.getMixer(info1);
        if (mixer.getMixerInfo().getName().equals(VoiceScapePlugin.getInstance().speakerName)) {
          info = (DataLine.Info) mixer.getLine(info);
          break;
        }
      }

      inSpeaker = (SourceDataLine) AudioSystem.getLine(info);
      inSpeaker.open(af);
      int bytesRead = 0;
      byte[] inSound = new byte[config.performanceMode() ? 1096 : 8192];
      updateSettings();
      while (bytesRead != -1) {
        inSpeaker.stop();
        bytesRead = soundIn.read(inSound);
        if (bytesRead >= 0) {
          inSpeaker.start();
          inSpeaker.write(inSound, 0, bytesRead);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      stopReceiver();
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
      connection.close();
      inSpeaker.stop();
      inSpeaker.close();
      soundIn.close();
      thread.interrupt();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
