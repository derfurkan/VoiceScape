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
  public Thread thread;
  private Socket connection;

  public final CompletableFuture<Boolean> completableFuture;

  public MessageThread messageThread;

  private TargetDataLine microphone;
  private boolean isRunning = true;

  public VoiceEngine(
      String serverIP,
      int serverPort,
      VoiceScapeConfig voiceScapeConfig) {
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
            if(mixer.getMixerInfo().getName().equals(VoiceScapePlugin.getInstance().microphoneName)) {
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
    }  catch (LineUnavailableException e) {
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
      byte[] soundData = new byte[1096];
      while (bytesRead != -1 && isRunning) {
        bytesRead = microphone.read(soundData, 0, 1096);
        if (bytesRead >= 0 && !voiceScapeConfig.muteSelf()) {
          dos.write(soundData, 0, bytesRead);
        } else if (voiceScapeConfig.muteSelf()) {
          dos.write(new byte[0], 0, 0);
        }
      }
    } catch (Exception e) {
      if (VoiceScapePlugin.isRunning && isRunning) {
        SwingUtilities.invokeLater(
            new Runnable() {
              public void run() {
                JOptionPane.showMessageDialog(
                    null,
                    "Connection to the voice server has been lost.\nHere are a few reasons why this could happen:\n- The server is not online\n- The server has rejected your request\n- You are using a proxy or VPN\n- You firewall/antivirus blocks the connection to the server\n\n"
                        + "Please wait one minute and try again.",
                    "VoiceScape - Error",
                    JOptionPane.ERROR_MESSAGE);
              }
            });
      }
      e.printStackTrace();
      stopEngine();
    }
  }

  public void stopEngine() {
    isRunning = false;
    try {
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
