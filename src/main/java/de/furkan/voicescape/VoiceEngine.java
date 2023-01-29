package de.furkan.voicescape;

import javax.sound.sampled.*;
import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

public class VoiceEngine implements Runnable {
  private final VoiceScapeConfig voiceScapeConfig;
  public VoiceReceiverThread voiceReceiverThread;
  private Socket connection;
  private TargetDataLine microphone;
  public Thread thread;

  public final CompletableFuture<Boolean> completableFuture;

  public VoiceEngine(String serverIP, int serverPort, VoiceScapeConfig voiceScapeConfig) {
    this.voiceScapeConfig = voiceScapeConfig;
    completableFuture = new CompletableFuture<>();
    try {
      this.thread = new Thread(this, "VoiceEngine");
      this.connection = new Socket(serverIP, serverPort);
      this.thread.start();
      completableFuture.complete(true);
    } catch (Exception e) {
      completableFuture.complete(false);
      e.printStackTrace();

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          JOptionPane.showMessageDialog(
                  null,
                  "Could not connect to the server. Please try again later.",
                  "Error",
                  JOptionPane.ERROR_MESSAGE);
        }
      });


      thread.interrupt();
    }
  }

  @Override
  public void run() {
    try {
      AudioFormat audioFormat = new AudioFormat(44100, 16, 2, true, true);
      DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
      microphone = (TargetDataLine) AudioSystem.getLine(info);
      microphone.open(audioFormat);
      microphone.start();
      OutputStream dos = connection.getOutputStream();
      int bytesRead = 0;
      byte[] soundData = new byte[1096];
      voiceReceiverThread = new VoiceReceiverThread(connection, voiceScapeConfig);
      while (bytesRead != -1) {
        bytesRead = microphone.read(soundData, 0, 1096);
        if (bytesRead >= 0 && !voiceScapeConfig.muteSelf()) {
          dos.write(soundData, 0, bytesRead);
        } else if (voiceScapeConfig.muteSelf()) {
          dos.write(new byte[0], 0, 0);
        }
      }
    } catch (LineUnavailableException e) {


      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          JOptionPane.showConfirmDialog(
                  null,
                  "Could not open microphone. Please check your microphone.",
                  "Error",
                  JOptionPane.ERROR_MESSAGE);
        }
      });

      e.printStackTrace();
      stopEngine();
    } catch (Exception e) {
      e.printStackTrace();
      stopEngine();
    }
  }

  public void stopEngine() {
    try {
      connection.close();
      microphone.stop();
      microphone.close();
      if(voiceReceiverThread != null)
        voiceReceiverThread.stopReceiver();

      thread.interrupt();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
