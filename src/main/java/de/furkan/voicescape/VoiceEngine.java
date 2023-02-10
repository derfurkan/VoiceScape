package de.furkan.voicescape;

import javax.sound.sampled.*;
import javax.swing.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class VoiceEngine implements Runnable {
  private final VoiceScapeConfig voiceScapeConfig;
  private final DatagramSocket connection;
  public VoiceReceiverThread voiceReceiverThread;
  public Thread thread;
  public MessageThread messageThread;
  private TargetDataLine microphone;
  private boolean isRunning = true;

  public VoiceEngine(DatagramSocket connection, VoiceScapeConfig voiceScapeConfig) {
    this.connection = connection;
    this.voiceScapeConfig = voiceScapeConfig;

    try {
      this.thread = new Thread(this, "VoiceEngine");
      AudioFormat audioFormat = new AudioFormat(44100, 16, 1, true, true);
      DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

      Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
      for (Mixer.Info info1 : mixerInfo) {
        Mixer mixer = AudioSystem.getMixer(info1);
        if (mixer.getMixerInfo().getName().equals(VoiceScapePlugin.getInstance().microphoneName)) {
          info = (DataLine.Info) mixer.getLine(info);
          break;
        }
      }

      microphone = (TargetDataLine) AudioSystem.getLine(info);
      microphone.open(audioFormat);
      microphone.start();
      voiceReceiverThread = new VoiceReceiverThread(connection, voiceScapeConfig);
      this.thread.start();
    } catch (LineUnavailableException e) {
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
      stopEngine();
    } catch (Exception e) {
      stopEngine();
      thread.interrupt();
    }
  }

  @Override
  public void run() {
    try {
      int bytesRead = 0;
      byte[] soundData = new byte[8192];
      while (bytesRead != -1 && isRunning && VoiceScapePlugin.isRunning) {
        bytesRead = microphone.read(soundData, 0, 8192);
        if (bytesRead >= 0
            && !voiceScapeConfig.muteSelf()
            && VoiceScapePlugin.getInstance().sendMicrophoneData) {
          VoicePacket rtp_packet = new VoicePacket(soundData, soundData.length);
          int packet_length = rtp_packet.getlength();
          byte[] packet_bits = new byte[packet_length];
          rtp_packet.getpacket(packet_bits);
          DatagramPacket packet = new DatagramPacket(packet_bits, packet_length);
          connection.send(packet);
        }
      }
      stopEngine();
    } catch (Exception e) {
      e.printStackTrace();
      stopEngine();
    }
  }

  public void stopEngine() {

    isRunning = false;
    try {
      if (messageThread != null) messageThread.stop();
      connection.close();
      microphone.close();
      microphone.stop();
      if (voiceReceiverThread != null) voiceReceiverThread.stopReceiver();
      thread.interrupt();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
