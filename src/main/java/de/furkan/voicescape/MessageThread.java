package de.furkan.voicescape;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import de.furkan.voicescape.jsocksSocksProxy.Socks5DatagramSocket;
import de.furkan.voicescape.jsocksSocksProxy.SocksException;
import de.furkan.voicescape.jsocksSocksProxy.SocksProxyBase;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;

import javax.inject.Inject;
import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MessageThread implements Runnable {
    public final Thread thread;

    private final Client client;
    private final VoiceScapeConfig config;
    public PrintWriter out;
    ArrayList<String> lastUpdate = Lists.newArrayList();
    Thread thread1;
    @Inject
    private Gson gson;
    private Socket connection;
    private Socks5DatagramSocket datagramSocket;

    public MessageThread(
            String ip, int port, Client client, VoiceScapeConfig config, Gson gsonInstance) {
        if (gsonInstance != null) {
            this.gson = gsonInstance;
        }
        this.config = config;
        this.client = client;
        this.thread = new Thread(this, "MessageThread");
        try {
            VoiceScapePlugin.overlay.currentLine = "Connecting to Message Server";

            if (config.useProxy() && config.proxyIPAndPort().contains(":")) {
                Authenticator.setDefault(getAuth(config.proxyUsername(), config.proxyPassword()));
                SocketAddress addr =
                        new InetSocketAddress(
                                config.proxyIPAndPort().split(":")[0],
                                Integer.parseInt(config.proxyIPAndPort().split(":")[1]));
                Proxy proxy = new Proxy(Proxy.Type.SOCKS, addr);
                connection = new Socket(proxy);
            } else {
                connection = new Socket();
            }

            connection.connect(new InetSocketAddress(ip, port), 10000);
            connection.setTcpNoDelay(true);
            connection.setKeepAlive(true);
            if (connection.isConnected()) {

                out = new PrintWriter(connection.getOutputStream(), true);
                new Timer()
                        .schedule(
                                new TimerTask() {
                                    @Override
                                    public void run() {
                                        try {

                                            if (config.useProxy() && config.proxyIPAndPort().contains(":")) {
                                                String proxyHost = config.proxyIPAndPort().split(":")[0];
                                                int proxyPort = Integer.parseInt(config.proxyIPAndPort().split(":")[1]);
                                                String proxyUser = config.proxyUsername();
                                                String proxyPass = config.proxyPassword();
                                                SocksProxyBase socksProxyBase =
                                                        SocksProxyBase.parseProxy(
                                                                proxyHost + ":" + proxyPort + ":" + proxyUser + ":" + proxyPass);
                                                SocksProxyBase.setDefaultProxy(socksProxyBase);
                                            }
                                            datagramSocket = new Socks5DatagramSocket();

                                            VoiceScapePlugin.overlay.currentLine = "Registering to Server";
                                            out.println(
                                                    "register:"
                                                            + VoiceScapePlugin.getInstance()
                                                            .hashWithSha256(client.getLocalPlayer().getName())
                                                            + "#"
                                                            + VoiceScapePlugin.uuidString);

                                            VoiceScapePlugin.overlay.currentLine = "Starting Voice Engine";
                                            VoiceScapePlugin.getInstance().voiceEngine =
                                                    new VoiceEngine(datagramSocket, config, client);
                                            VoiceScapePlugin.overlay.currentLine = "Waiting for update";
                                        } catch (SocksException socksException) {
                                            VoiceScapePlugin.getInstance().shutdownAll(socksException.getMessage());
                                            socksException.lookupErrorString(socksException.getErrorCode());
                                            SwingUtilities.invokeLater(
                                                    new Runnable() {
                                                        public void run() {
                                                            int option =
                                                                    JOptionPane.showConfirmDialog(
                                                                            null,
                                                                            "There was an error with the proxy.\nBe sure that the proxyType is SOCKS5 and it can process voice packets.\n\nError: "
                                                                                    + socksException.errString
                                                                                    + " | Code: "
                                                                                    + socksException.getErrorCode()
                                                                                    + "\n\nDo you want to try again?",
                                                                            "VoiceScape - Error",
                                                                            JOptionPane.YES_NO_OPTION,
                                                                            JOptionPane.ERROR_MESSAGE);
                                                            if (option == JOptionPane.YES_OPTION) {
                                                                VoiceScapePlugin.getInstance().runPluginThreads(client, config);
                                                            }
                                                        }
                                                    });

                                            socksException.printStackTrace();
                                            thread.interrupt();
                                        } catch (Exception e) {
                                            VoiceScapePlugin.getInstance().shutdownAll(e.getMessage());
                                            SwingUtilities.invokeLater(
                                                    new Runnable() {
                                                        public void run() {
                                                            int option =
                                                                    JOptionPane.showConfirmDialog(
                                                                            null,
                                                                            "Could not connect to the server.\nDo you want to try again?",
                                                                            "VoiceScape - Error",
                                                                            JOptionPane.YES_NO_OPTION,
                                                                            JOptionPane.ERROR_MESSAGE);
                                                            if (option == JOptionPane.YES_OPTION) {
                                                                VoiceScapePlugin.getInstance().runPluginThreads(client, config);
                                                            }
                                                        }
                                                    });
                                            e.printStackTrace();
                                            thread.interrupt();
                                        }
                                    }
                                },
                                1200);
            }
            this.thread.start();
        } catch (Exception e) {
            VoiceScapePlugin.getInstance().shutdownAll(e.getMessage());
            SwingUtilities.invokeLater(
                    new Runnable() {
                        public void run() {
                            if (e.getMessage().contains("SOCKS")) {
                                int option =
                                        JOptionPane.showConfirmDialog(
                                                null,
                                                "There was an error with the proxy.\nBe sure that the proxyType is SOCKS5 and it can process voice packets.\n\nError: "
                                                        + e.getMessage()
                                                        + "\n\nDo you want to try again?",
                                                "VoiceScape - Error",
                                                JOptionPane.YES_NO_OPTION,
                                                JOptionPane.ERROR_MESSAGE);
                                if (option == JOptionPane.YES_OPTION) {
                                    VoiceScapePlugin.getInstance().runPluginThreads(client, config);
                                }
                            } else {
                                int option =
                                        JOptionPane.showConfirmDialog(
                                                null,
                                                "Could not connect to the server.\nDo you want to try again?",
                                                "VoiceScape - Error",
                                                JOptionPane.YES_NO_OPTION,
                                                JOptionPane.ERROR_MESSAGE);
                                if (option == JOptionPane.YES_OPTION) {
                                    VoiceScapePlugin.getInstance().runPluginThreads(client, config);
                                }
                            }
                        }
                    });

            e.printStackTrace();
            thread.interrupt();
        }
    }

    @Override
    public void run() {
        thread1 =
                new Thread(
                        () -> {
                            try {
                                BufferedReader in =
                                        new BufferedReader(new InputStreamReader(connection.getInputStream()));
                                String line;
                                while ((line = in.readLine()) != null && VoiceScapePlugin.isRunning) {

                                    if (line.startsWith("register ")) {
                                        VoiceScapePlugin.lastUpdateMessage = System.currentTimeMillis();
                                        if (!VoiceScapePlugin.overlay.currentLine.equals("")) {
                                            VoiceScapePlugin.overlay.currentLine = "Updating Players";
                                        }
                                        line = line.replace("register ", "");
                                        ArrayList<String> registeredPlayers = new ArrayList<>();
                                        registeredPlayers = gson.fromJson(line, registeredPlayers.getClass());

                                        registeredPlayers.forEach(
                                                s -> {
                                                    if (!VoiceScapePlugin.registeredPlayers.contains(s))
                                                        VoiceScapePlugin.registeredPlayers.add(s);
                                                });

                                    } else if (line.startsWith("unregister ")) {
                                        if (VoiceScapePlugin.overlay.currentLine.equals("Updating Players")) {
                                            VoiceScapePlugin.overlay.currentLine = "";
                                        }
                                        line = line.replace("unregister ", "");
                                        ArrayList<Object> unregisteredPlayers = new ArrayList<>();
                                        unregisteredPlayers = gson.fromJson(line, unregisteredPlayers.getClass());
                                        unregisteredPlayers.forEach(s -> VoiceScapePlugin.registeredPlayers.remove(s));
                                    } else {
                                        String finalLine = line;
                                        SwingUtilities.invokeLater(
                                                () -> {
                                                    VoiceScapePlugin.getInstance().shutdownAll("Invalid Message");
                                                    JOptionPane.showConfirmDialog(
                                                            null,
                                                            "The Server has sent an invalid message. For security reasons you have been disconnected.\nInvalid server messages indicate that the server might be modified.\nPlease contact the server owner to fix this issue\n\nMessage\n"
                                                                    + finalLine,
                                                            "VoiceScape - Invalid Message",
                                                            JOptionPane.DEFAULT_OPTION,
                                                            JOptionPane.ERROR_MESSAGE);
                                                });
                                    }
                                }

                                if (!VoiceScapePlugin.alreadyShutDown) {
                                    VoiceEngine.connectionLostMessage(client, config);
                                }
                                VoiceScapePlugin.getInstance().shutdownAll("Connection to server lost");
                            } catch (Exception e) {
                                e.printStackTrace();
                                if (VoiceScapePlugin.isRunning && !config.useProxy()) {
                                    VoiceScapePlugin.getInstance().shutdownAll(e.getMessage());
                                    VoiceEngine.connectionLostMessage(client, config);
                                }

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

                                                            if (player.getName().equals(client.getLocalPlayer().getName()))
                                                                return;

                                                            if (client
                                                                    .getLocalPlayer()
                                                                    .getWorldLocation()
                                                                    .distanceTo(player.getWorldLocation())
                                                                    <= config.minDistance()
                                                                    && VoiceScapePlugin.registeredPlayers.contains(
                                                                    VoiceScapePlugin.getInstance()
                                                                            .hashWithSha256(player.getName()))
                                                                    && !VoiceScapePlugin.mutedPlayers.contains(player.getName())) {
                                                                finalPlayerNames.add(
                                                                        VoiceScapePlugin.getInstance()
                                                                                .hashWithSha256(player.getName()));
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
                if (VoiceScapePlugin.registeredPlayers.contains(
                        VoiceScapePlugin.getInstance().hashWithSha256(player.getName()))) {
                    return true;
                }
            }
        }
        return false;
    }

    public Authenticator getAuth(String user, String password) {
        new Authenticator() {
            public PasswordAuthentication getPasswordAuthentication() {
                return (new PasswordAuthentication(user, password.toCharArray()));
            }
        };
        return null;
    }

    public void stop() {
        try {

            if (connection != null) connection.close();
            if (out != null) out.close();
            thread.interrupt();
            if (thread1 != null)
                thread1.interrupt();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
