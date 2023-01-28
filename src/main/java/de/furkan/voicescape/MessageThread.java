package de.furkan.voicescape;

import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class MessageThread implements Runnable {
  private final Thread thread;
  private final Client client;
  private final VoiceScapeConfig config;
  private final Gson gson;
  public PrintWriter out;
  private BufferedReader in;
  private Socket connection;

  public MessageThread(
      String ip, int port, Client client, VoiceScapeConfig config, Gson gsonInstance) {
    this.gson = gsonInstance;
    this.config = config;
    this.client = client;
    this.thread = new Thread(this, "MessageThread");
    this.thread.start();
    try {
      connection = new Socket();
      connection.connect(new InetSocketAddress(ip, port), 3000);
      out = new PrintWriter(connection.getOutputStream(), true);
      in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    } catch (Exception e) {
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
                  HashMap<String, Integer[]> allPlayers = new HashMap<>();
                  client
                      .getPlayers()
                      .forEach(
                          player -> {
                            if (player != null && player.getName() != null) {
                              if (player.getName().equals(client.getLocalPlayer().getName())
                                  && !config.loopback()) {
                                return;
                              }
                              if (client
                                      .getLocalPlayer()
                                      .getWorldLocation()
                                      .distanceTo(
                                          new WorldPoint(
                                              player.getWorldLocation().getX(),
                                              player.getWorldLocation().getY(),
                                              0))
                                  <= config.minDistance()) {
                                allPlayers.put(
                                    player.getName(),
                                    new Integer[] {
                                      player.getWorldLocation().getX(),
                                      player.getWorldLocation().getY()
                                    });
                              }
                            }
                          });

                  String json = gson.toJson(allPlayers);
                  out.println(json);
                } else {
                  cancel();
                }
              }
            },
            1000,
            1000);
    out.println("register:" + client.getLocalPlayer().getName());
  }

  public void stopMessageThread() {
    try {
      connection.close();
      in.close();
      out.close();
      thread.interrupt();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
