package de.furkan.voicescape;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;

import java.io.IOException;
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

  private Socket connection;
  public ArrayList<Player> lastUpdate = Lists.newArrayList();

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

    new Timer()
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                if (client.getGameState() == GameState.LOGGED_IN && !thread.isInterrupted()) {

                  if (VoiceScapePlugin.nearSpawnedPlayers.size() == 0
                      || VoiceScapePlugin.nearSpawnedPlayers.equals(lastUpdate)) return;

                  ArrayList<String> playerNames = Lists.newArrayList();
                  VoiceScapePlugin.nearSpawnedPlayers.forEach(
                      player -> {

                        if (player == null || player.getName() == null) return;
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
                  out.println(gson.toJson(playerNames));
                  playerNames.clear();

                  lastUpdate = (ArrayList<Player>) VoiceScapePlugin.nearSpawnedPlayers.clone();
                } else {
                  cancel();
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
