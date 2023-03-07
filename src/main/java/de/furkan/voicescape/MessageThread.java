package de.furkan.voicescape;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import de.furkan.voicescape.jsocksSocksProxy.Socks5DatagramSocket;
import de.furkan.voicescape.jsocksSocksProxy.SocksException;
import de.furkan.voicescape.jsocksSocksProxy.SocksProxyBase;
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
            VoiceScapePlugin.overlay.currentLine = "Connecting to Message Server";
            // Connect to numeric IP
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

            connection.connect(new InetSocketAddress(ip, port), 5000);
            if (connection.isConnected()) {
                connection.setTcpNoDelay(true);
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
                                            Socks5DatagramSocket datagramSocket = new Socks5DatagramSocket();

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
                                            VoiceScapePlugin.isRunning = true;
                                            VoiceScapePlugin.overlay.currentLine = "Waiting for update";
                                        } catch (SocksException socksException) {
                                            VoiceScapePlugin.getInstance().shutdownAll();
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
                                            VoiceScapePlugin.getInstance().shutdownAll();
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
            VoiceScapePlugin.getInstance().shutdownAll();
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
                                    VoiceEngine.connectionLostMessage(client, config);
                                }
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
