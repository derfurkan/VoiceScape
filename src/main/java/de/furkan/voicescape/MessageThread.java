package de.furkan.voicescape;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.GameState;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
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
      connection = new Socket(ip, port);
      out = new PrintWriter(connection.getOutputStream(), true);
      out.println("register:" + client.getLocalPlayer().getName());
      this.thread.start();
    } catch (Exception e) {
      e.printStackTrace();
      thread.interrupt();
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
                while ((line = in.readLine()) != null) {
                  if (line.startsWith("register:")) {
                    line = line.replace("register:", "");
                    VoiceScapePlugin.registeredPlayers.add(line);
                  } else if (line.startsWith("unregister:")) {
                    line = line.replace("unregister:", "");
                    VoiceScapePlugin.registeredPlayers.remove(line);
                  }
                }
              } catch (IOException e) {
                try {
                  JOptionPane.showConfirmDialog(
                      null,
                      "The Server has sent an invalid message.\nFor security reasons you have been disconnected.\nInvalid server messages indicate that the server might be modified.\nPlease contact the server owner to fix this issue\n\nMessage\n"
                          + e.getMessage(),
                      "VoiceScape - Invalid Message",
                      JOptionPane.DEFAULT_OPTION,
                      JOptionPane.ERROR_MESSAGE);
                  VoiceScapePlugin.getInstance().shutDown();
                } catch (Exception ex) {
                  throw new RuntimeException(ex);
                }
                e.printStackTrace();
              }
            });

    thread1.start();

    new Timer()
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                if (client.getGameState() == GameState.LOGGED_IN && !thread.isInterrupted()) {

                  if (client.getPlayers().size() == 0) return;

                  ArrayList<String> playerNames = Lists.newArrayList();
                  client
                      .getPlayers()
                      .forEach(
                          player -> {
                            if (player == null
                                || player.getName() == null
                                || client.getPlayers().size() == 0) return;

                            if (config.connectionIndicator()
                                && client
                                        .getLocalPlayer()
                                        .getWorldLocation()
                                        .distanceTo(player.getWorldLocation())
                                    <= config.indicatorDistance()
                                && (player.getOverheadText() == null
                                    || player.getOverheadText().isEmpty())
                                && VoiceScapePlugin.registeredPlayers.contains(player.getName())) {

                              if (player.getName().equals(client.getLocalPlayer().getName())
                                  && !config.showOwnIndicator()) {
                                player.setOverheadText("");
                                VoiceScapePlugin.indicatedPlayers.remove(player);
                              } else {
                                if (!VoiceScapePlugin.indicatedPlayers.contains(player)) {
                                  VoiceScapePlugin.indicatedPlayers.add(player);
                                }
                                player.setOverheadText("Connected [" + player.getName() + "]");
                              }
                            } else if (player.getOverheadText() != null
                                && player.getOverheadText().startsWith("Connected")
                                && (!config.connectionIndicator()
                                    || client
                                            .getLocalPlayer()
                                            .getWorldLocation()
                                            .distanceTo(player.getWorldLocation())
                                        > config.indicatorDistance())) {
                              player.setOverheadText("");
                              VoiceScapePlugin.indicatedPlayers.remove(player);
                            }

                            if (player.getName().equals(client.getLocalPlayer().getName())
                                && !config.loopback()) return;

                            if (client
                                    .getLocalPlayer()
                                    .getWorldLocation()
                                    .distanceTo(player.getWorldLocation())
                                <= config.minDistance()) {
                              playerNames.add(player.getName());
                            }
                          });

                  if (playerNames.size() != 0 && !playerNames.equals(lastUpdate)) {
                    out.println(gson.toJson(playerNames));
                    lastUpdate = playerNames;
                  }
                } else {
                  cancel();
                  thread1.interrupt();
                }
              }
            },
            1000,
            1000);
  }

  public void stop() {
    try {
      connection.close();
      out.close();
      thread.interrupt();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
