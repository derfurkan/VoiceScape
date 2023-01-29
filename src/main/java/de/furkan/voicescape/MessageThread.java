package de.furkan.voicescape;

import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class MessageThread implements Runnable {
  public final Thread thread;
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
    try {
      connection = new Socket(ip, port);
      out = new PrintWriter(connection.getOutputStream(), true);
      out.println("register:" + client.getLocalPlayer().getName());
      in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    } catch (Exception e) {

      thread.interrupt();
    }
    this.thread.start();
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
            3000,
            3000);

  }
}
