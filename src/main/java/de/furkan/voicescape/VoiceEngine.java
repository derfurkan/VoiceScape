package de.furkan.voicescape;

import javax.sound.sampled.*;
import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

public class VoiceEngine implements Runnable {

  public final CompletableFuture<Boolean> completableFuture;
  private final VoiceScapeConfig voiceScapeConfig;
  public VoiceReceiverThread voiceReceiverThread;
  public Thread thread;
  public MessageThread messageThread;
  public boolean sendEmpty = false;
  boolean noise = false;
  private Socket connection;
  private TargetDataLine microphone;
  private boolean isRunning = true;

  public VoiceEngine(String serverIP, int serverPort, VoiceScapeConfig voiceScapeConfig) {
    completableFuture = new CompletableFuture<>();
    this.voiceScapeConfig = voiceScapeConfig;

    try {
      this.thread = new Thread(this, "VoiceEngine");
      this.connection = new Socket(serverIP, serverPort);
      AudioFormat audioFormat = new AudioFormat(44100, 16, 2, true, true);
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
      completableFuture.complete(true);
    } catch (LineUnavailableException e) {
      completableFuture.complete(false);
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

      e.printStackTrace();
      microphone.close();
      microphone.stop();
      stopEngine();
    } catch (Exception e) {
      completableFuture.complete(false);
      e.printStackTrace();
      SwingUtilities.invokeLater(
          new Runnable() {
            public void run() {
              JOptionPane.showMessageDialog(
                  null,
                  "Could not connect to the server. Please try again later.",
                  "VoiceScape - Error",
                  JOptionPane.ERROR_MESSAGE);
            }
          });
      thread.interrupt();
    }
  }

  @Override
  public void run() {
    try {
      OutputStream dos = connection.getOutputStream();
      int bytesRead = 0;
      byte[] soundData = new byte[8192];
      while (bytesRead != -1 && isRunning) {
        bytesRead = microphone.read(soundData, 0, 8192);
        if (bytesRead >= 0 && !voiceScapeConfig.muteSelf()) {
          sendEmpty = false;
          dos.write(soundData, 0, bytesRead);
        } else if (voiceScapeConfig.muteSelf()) {
          if (!sendEmpty) {
            dos.write(new byte[8192], 0, 8192);
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      stopEngine();
    }
  }

  public void stopEngine() {

    isRunning = false;
    try {

      if(messageThread != null)
        messageThread.stop();

      connection.close();
      microphone.close();
      microphone.stop();
      if (voiceReceiverThread != null) voiceReceiverThread.stopReceiver();
      thread.interrupt();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
