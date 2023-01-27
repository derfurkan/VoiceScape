package de.furkan.voicescape;

import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;


public class MessageThread implements Runnable {
    private final Thread thread;
    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;
    private final Client client;
    private final VoiceScapeConfig config;

    private final Gson gson;

    public MessageThread(String ip, int port, Client client, VoiceScapeConfig config,Gson gson) {
        this.gson = gson;
        this.config = config;
        this.client = client;
        this.thread = new Thread(this, "MessageThread");
        try {
            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        thread.start();
    }

    public void sendMessageToServer(String msg) {
        out.println(msg);
    }

    public void stopConnection() {
        try {
            in.close();
            out.close();
            socket.close();
            thread.interrupt();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void run() {


        new Timer().schedule(new TimerTask() {

            @Override
            public void run() {
                if (client.getGameState() == GameState.LOGGED_IN) {
                    HashMap<String, Integer[]> allPlayers = new HashMap<>();
                    client.getPlayers().forEach(player -> {
                        if (player != null && player.getName() != null) {
                           if(player.getName().equals(client.getLocalPlayer().getName()) && !config.loopback()) {
                                return;
                            }
                            if(client.getLocalPlayer().getWorldLocation().distanceTo(new WorldPoint(player.getWorldLocation().getX(), player.getWorldLocation().getY(), 0)) <= config.minDistance()) {
                               allPlayers.put(player.getName(), new Integer[]{player.getWorldLocation().getX(), player.getWorldLocation().getY()});
                           }
                        }
                    });

                    String json = gson.toJson(allPlayers);
                    sendMessageToServer(json);
                }

            }
        }, 1000, 1000);


        sendMessageToServer("register:" + client.getLocalPlayer().getName());
    }


}
