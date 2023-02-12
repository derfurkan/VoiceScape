package de.furkan.voicescape;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MessageThread implements Runnable {
  public final Thread thread;

  private final Client client;
  private final VoiceScapeConfig config;
  private final Gson gson;
  public PrintWriter out;
  ArrayList<String> lastUpdate = Lists.newArrayList();
  private Socket connection;

  public MessageThread(
      String ip, int port, Client client, VoiceScapeConfig config, Gson gsonInstance) {
    this.gson = gsonInstance;
    this.config = config;
    this.client = client;
    this.thread = new Thread(this, "MessageThread");
    try {
      connection = new Socket();
      connection.connect(new InetSocketAddress(ip, port), 5000);
      connection.setTcpNoDelay(true);
      out = new PrintWriter(connection.getOutputStream(), true);
      new Timer()
          .schedule(
              new TimerTask() {
                @Override
                public void run() {
                  try {
                    DatagramSocket datagramSocket = new DatagramSocket(findRandomOpenPort());
                    datagramSocket.connect(InetAddress.getByName(ip), 24444);
                    out.println(
                        "register:"
                            + client.getLocalPlayer().getName()
                            + "#"
                            + datagramSocket.getLocalPort());
                    VoiceScapePlugin.getInstance().voiceEngine =
                        new VoiceEngine(datagramSocket, config);
                  } catch (Exception e) {
                    e.printStackTrace();
                    thread.interrupt();
                  }
                }
              },
              1200);
      this.thread.start();
    } catch (Exception e) {
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
      e.printStackTrace();
      thread.interrupt();
    }
  }

  private int findRandomOpenPort() {
    try (ServerSocket socket = new ServerSocket(new SecureRandom().nextInt(65535))) {
      return socket.getLocalPort();
    } catch (IOException e) {
      return findRandomOpenPort();
    }
  }

  @Override
  public void run() {
    Thread thread1 =
        new Thread(
            () -> {
              try {
                BufferedReader in =
                    new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                while ((line = in.readLine()) != null && VoiceScapePlugin.isRunning) {
                  if (line.startsWith("register ")) {
                    line = line.replace("register ", "");
                    ArrayList<String> registeredPlayers = new ArrayList<>();
                    registeredPlayers = gson.fromJson(line, registeredPlayers.getClass());

                    registeredPlayers.forEach(
                        s -> {
                          if (!VoiceScapePlugin.registeredPlayers.contains(s))
                            VoiceScapePlugin.registeredPlayers.add(s);
                        });

                  } else if (line.startsWith("unregister ")) {
                    line = line.replace("unregister ", "");
                    ArrayList<String> unregisteredPlayers = new ArrayList<>();
                    unregisteredPlayers = gson.fromJson(line, unregisteredPlayers.getClass());
                    unregisteredPlayers.forEach(s -> VoiceScapePlugin.registeredPlayers.remove(s));
                  } else {
                    String finalLine = line;
                    SwingUtilities.invokeLater(
                        () -> {
                          JOptionPane.showConfirmDialog(
                              null,
                              "The Server has sent an invalid message. For security reasons you have been disconnected.\nInvalid server messages indicate that the server might be modified.\nPlease contact the server owner to fix this issue\n\nMessage\n"
                                  + finalLine,
                              "VoiceScape - Invalid Message",
                              JOptionPane.DEFAULT_OPTION,
                              JOptionPane.ERROR_MESSAGE);
                        });
                    try {
                      VoiceScapePlugin.getInstance().shutdownAll();
                    } catch (Exception ex) {
                      throw new RuntimeException(ex);
                    }
                  }
                }
                VoiceScapePlugin.getInstance().shutdownAll();
              } catch (Exception e) {
                if (VoiceScapePlugin.isRunning) {
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
                VoiceScapePlugin.indicatedPlayers.forEach(player -> player.setOverheadText(""));
                VoiceScapePlugin.getInstance().shutdownAll();
              }
            });

    thread1.start();

    new Timer()
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                if ((client.getGameState() == GameState.LOGGED_IN
                        || client.getGameState() == GameState.HOPPING
                        || client.getGameState() == GameState.LOADING)
                    && !thread.isInterrupted()
                    && VoiceScapePlugin.isRunning) {

                  ArrayList<String> playerNames = Lists.newArrayList();
                  ArrayList<String> finalPlayerNames = playerNames;
                  if (client.getPlayers().size() > 0 && containsRegisteredPlayers()) {
                    client
                        .getPlayers()
                        .forEach(
                            player -> {
                              if (player == null || player.getName() == null) return;
                              String indicatorText =
                                  config.indicatorString().replaceAll("%p", player.getName());
                              if (config.connectionIndicator()
                                  && client
                                          .getLocalPlayer()
                                          .getWorldLocation()
                                          .distanceTo(player.getWorldLocation())
                                      <= config.indicatorDistance()
                                  && (player.getOverheadText() == null
                                      || player.getOverheadText().isEmpty())
                                  && VoiceScapePlugin.registeredPlayers.contains(
                                      player.getName())) {

                                if (player.getName().equals(client.getLocalPlayer().getName())
                                    && !config.showOwnIndicator()) {
                                  player.setOverheadText("");
                                  VoiceScapePlugin.indicatedPlayers.remove(player);
                                } else {
                                  if (!VoiceScapePlugin.indicatedPlayers.contains(player)) {
                                    VoiceScapePlugin.indicatedPlayers.add(player);
                                  }
                                  player.setOverheadText(indicatorText);
                                }
                              } else if (player.getOverheadText() != null
                                  && player.getOverheadText().equals(indicatorText)
                                  && (!config.connectionIndicator()
                                      || client
                                              .getLocalPlayer()
                                              .getWorldLocation()
                                              .distanceTo(player.getWorldLocation())
                                          > config.indicatorDistance()
                                      || !VoiceScapePlugin.registeredPlayers.contains(
                                          player.getName()))) {
                                player.setOverheadText("");
                                VoiceScapePlugin.indicatedPlayers.remove(player);
                              }

                              if (player.getName().equals(client.getLocalPlayer().getName()))
                                return;

                              if (client
                                          .getLocalPlayer()
                                          .getWorldLocation()
                                          .distanceTo(player.getWorldLocation())
                                      <= config.minDistance()
                                  && VoiceScapePlugin.registeredPlayers.contains(player.getName())
                                  && !VoiceScapePlugin.mutedPlayers.contains(player.getName())) {
                                finalPlayerNames.add(player.getName());
                              }
                            });
                  }

                  if (playerNames.size() > config.maxClients() && config.performanceMode()) {
                    playerNames = Lists.newArrayList(playerNames.subList(0, config.maxClients()));
                  }
                  VoiceScapePlugin.getInstance().sendMicrophoneData = playerNames.size() > 0;
                  if (!playerNames.equals(lastUpdate)) {
                    out.println(gson.toJson(playerNames));
                    lastUpdate = playerNames;
                  }
                } else {
                  cancel();
                  thread1.interrupt();
                }
              }
            },
            7000,
            1000);
  }

  private boolean containsRegisteredPlayers() {
    if (client.getPlayers().size() > 0) {
      for (Player player : client.getPlayers()) {
        if (player == null || player.getName() == null) continue;
        if (VoiceScapePlugin.registeredPlayers.contains(player.getName())) {
          return true;
        }
      }
    }
    return false;
  }

  public void stop() {
    try {

      if (connection != null) connection.close();
      if (out != null) out.close();
      thread.interrupt();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
