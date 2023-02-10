package de.furkan.voicescape;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class VoiceReceiverThread implements Runnable {
  final DatagramSocket connection;
  final Thread thread;
  private final VoiceScapeConfig config;
  SourceDataLine inSpeaker;

  public VoiceReceiverThread(DatagramSocket connection, VoiceScapeConfig voiceScapeConfig) {
    this.config = voiceScapeConfig;
    this.thread = new Thread(this, "VoiceReceiverThread");
    this.connection = connection;
    this.thread.start();
  }

  public void run() {
    try {
      AudioFormat af = new AudioFormat(44100, 16, 1, true, true);
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
      byte[] inSound = new byte[8192];
      updateSettings();
      while (bytesRead != -1 && VoiceScapePlugin.isRunning) {
        inSpeaker.stop();
        DatagramPacket packet = new DatagramPacket(inSound, inSound.length);
        connection.receive(packet);
        VoicePacket rtpPacket = new VoicePacket(packet.getData(), packet.getLength());
        int payload_length = rtpPacket.getpayload_length();
        byte[] payload = new byte[payload_length];
        rtpPacket.getpayload(payload);

        bytesRead = packet.getLength();
        if (bytesRead >= 0) {
          inSpeaker.start();
          inSpeaker.write(payload, 0, payload_length);
        }
      }
      stopReceiver();
    } catch (Exception e) {
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
      thread.interrupt();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
