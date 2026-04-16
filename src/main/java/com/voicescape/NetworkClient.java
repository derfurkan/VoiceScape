package com.voicescape;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
public class NetworkClient
{
	
	private static final byte MSG_HELLO = 0x01;
	private static final byte MSG_HASH_LIST_UPDATE = 0x02;
	private static final byte MSG_AUDIO_FRAME = 0x03;
	private static final byte MSG_IDENTITY = 0x04;

	
	private static final byte MSG_HELLO_ACK = 0x10;
	private static final byte MSG_KEY_ROTATION = 0x11;
	private static final byte MSG_AUDIO_FRAME_FROM_SERVER = 0x12;
	private static final byte MSG_SERVER_ERROR = 0x13;

	private static final int PROTOCOL_VERSION = 1; // Change for later versions only for sever compatibillity
	private static final int MAX_PACKET_SIZE = 1400;
	private static final int MAX_AUDIO_PAYLOAD = 1400;
	private static final byte MSG_UDP_REGISTER = 0x20;
	private static final int UDP_BUFFER_SIZE = 2048; // Increased to fit raw PCM frames plus headers
	private static final long UDP_REGISTER_INTERVAL_MS = 30_000;
	private static final long HEARTBEAT_INTERVAL_MS = 10_000;
	private static final long RECONNECT_DELAY_MS = 5_000;
	private static final long HANDSHAKE_TIMEOUT_MS = 10_000;

	private final VoiceChatConfig config;
	private final AudioPlaybackManager playbackManager;
	@Getter
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicInteger audioSequenceNumber = new AtomicInteger(0);

	private volatile Socket socket;
	private volatile DataOutputStream out;
	private volatile DataInputStream in;

	private volatile String sessionId="";
	@Getter
	private volatile byte[] currentDailyKey;
	private volatile byte[] udpKey;
	private volatile String playerName;
	private volatile long lastHashListTime = 0;
	private volatile long lastUdpRegisterTime = 0;

	private volatile DatagramSocket udpSocket;
	private volatile InetAddress serverAddr;
	private volatile int serverPort;

	private Thread readThread;
	private Thread heartbeatThread;
	private Thread udpReceiveThread;

	@Setter
    private volatile Consumer<String> statusListener;

	public NetworkClient(VoiceChatConfig config, AudioPlaybackManager playbackManager)
	{
		this.config = config;
		this.playbackManager = playbackManager;
	}

    private void reportStatus(String message)
	{
		Consumer<String> l = statusListener;
		if (l != null)
		{
			l.accept(message);
		}
	}


	public boolean isConnected()
	{
		Socket s = socket;
		return s != null && s.isConnected() && !s.isClosed();
	}

	public void connect(String playerName)
	{
		disconnect();
		this.playerName = playerName;
		running.set(true);

		readThread = new Thread(this::connectionLoop, "VoiceScape-Network");
		readThread.setDaemon(true);
		readThread.start();
	}

	public void disconnect()
	{
		running.set(false);
		reportStatus("Disconnected");
		closeSocket();
		if (readThread != null)
		{
			readThread.interrupt();
		}
		if (heartbeatThread != null)
		{
			heartbeatThread.interrupt();
		}
		if (udpReceiveThread != null)
		{
			udpReceiveThread.interrupt();
		}
	}

	private String computeIdentityHash()
	{
		byte[] key = currentDailyKey;
		String name = playerName;
		if (key == null || name == null || name.isEmpty())
		{
			return "";
		}
		return HashUtil.hmac(key, name);
	}

	public synchronized void sendHashListUpdate(List<String> nearbyHashes)
	{
		if (!isConnected() || out == null)
		{
			return;
		}

		try
		{
			int limit = Math.min(nearbyHashes.size(), 50);
			ByteArrayOutputStream entries = new ByteArrayOutputStream();
			DataOutputStream entryWriter = new DataOutputStream(entries);
			int count = 0;
			for (int i = 0; i < limit && count < limit; i++)
			{
				byte[] hashBytes = nearbyHashes.get(i).getBytes(StandardCharsets.UTF_8);
				if (hashBytes.length > 255)
				{
					continue;
				}
				entryWriter.writeShort(hashBytes.length);
				entryWriter.write(hashBytes);
				count++;
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream msg = new DataOutputStream(baos);
			msg.writeByte(MSG_HASH_LIST_UPDATE);
			msg.writeShort(count);
			msg.write(entries.toByteArray());
			writeFramed(out, baos.toByteArray());
			lastHashListTime = System.currentTimeMillis();
		}
		catch (IOException e)
		{
			log.error("Failed to send hash list update", e);
			closeSocket();
		}
	}

	public void sendAudioFrame(int sequenceNumber, byte[] opusPayload)
	{
		if (!isConnected())
		{
			return;
		}

		if (opusPayload.length > MAX_AUDIO_PAYLOAD)
		{
			log.debug("Audio payload too large ({} bytes), dropping", opusPayload.length);
			return;
		}

		DatagramSocket ds = udpSocket;
		InetAddress addr = serverAddr;
		byte[] key = udpKey;
		if (ds == null || ds.isClosed() || addr == null || key == null)
		{
			return;
		}

		try
		{
			byte[] encrypted = UdpCrypto.encrypt(key, sequenceNumber, opusPayload);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream msg = new DataOutputStream(baos);
			msg.writeByte(MSG_AUDIO_FRAME);
			msg.writeInt(sequenceNumber);
			msg.write(encrypted);
			byte[] data = baos.toByteArray();
			ds.send(new DatagramPacket(data, data.length, addr, serverPort));
		}
		catch (IOException e)
		{
			log.debug("UDP audio send failed: {}", e.getMessage());
		}
	}

	public int nextSequenceNumber()
	{
		return audioSequenceNumber.getAndIncrement();
	}

	private Socket createSocket(String host, int port) throws Exception
	{
		return new Socket(host, port);
	}

	public void performServerHandshake() throws IOException {
		sendHello();
		readHelloAck();
		sendIdentity(computeIdentityHash());
	}

	private void connectionLoop()
	{
		while (running.get())
		{
			try
			{
				String address = config.serverAddress();
				String[] parts = address.split(":");
				String host = parts[0];
				int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 5555;

				log.info("Connecting to voice server at {}:{}", host, port);
				reportStatus("Connecting to " + host + ":" + port + "...");

				socket = createSocket(host, port);
				socket.setTcpNoDelay(true);
				socket.setKeepAlive(true);
				socket.setSoTimeout((int) HANDSHAKE_TIMEOUT_MS);

				out = new DataOutputStream(socket.getOutputStream());
				in = new DataInputStream(socket.getInputStream());
				performServerHandshake();

				socket.setSoTimeout(0);
				log.info("Connected to voice server, session: {}", sessionId);
				reportStatus("Connected");
				openUdpChannel(host, port);
				startHeartbeat();

				while (running.get() && isConnected())
				{
					readMessage();
				}
			}
			catch (java.net.ConnectException e)
			{
				if (running.get())
				{
					log.warn("Connection refused: {}", e.getMessage());
					reportStatus("Connection refused");
				}
			}
			catch (java.net.UnknownHostException e)
			{
				if (running.get())
				{
					log.warn("Unknown host: {}", e.getMessage());
					reportStatus("Unknown host");
				}
			}
			catch (java.net.SocketTimeoutException e)
			{
				if (running.get())
				{
					log.warn("Connection timed out: {}", e.getMessage());
					reportStatus("No response from server");
				}
			}
			catch (Exception e)
			{
				if (running.get())
				{
					log.warn("Connection failed: {}", e.getMessage());
				}
			}
			finally
			{
				closeSocket();
			}

			if (running.get())
			{
				try
				{
					log.debug("Reconnecting in {}ms", RECONNECT_DELAY_MS);
					Thread.sleep(RECONNECT_DELAY_MS);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
	}

	private void writeFramed(DataOutputStream dst, byte[] payload) throws IOException
	{
		if(payload.length>MAX_PACKET_SIZE) {
			throw new IOException("Sending frame too large: " + payload.length);
		}
		dst.writeShort(payload.length);
		dst.write(payload);
		dst.flush();
	}

	private DataInputStream readFrame() throws IOException
	{
		DataInputStream input = in;
		if (input == null)
		{
			throw new IOException("Connection closed");
		}
		int length = input.readUnsignedShort();
		if (length > MAX_PACKET_SIZE)
		{
			throw new IOException("Incoming frame too large: " + length);
		}
		byte[] buf = new byte[length];
		input.readFully(buf);
		return new DataInputStream(new java.io.ByteArrayInputStream(buf));
	}

	private synchronized void sendHello() throws IOException
	{
		byte[] sessionIdBytes = sessionId.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		DataOutputStream msg = new DataOutputStream(byteArrayOutputStream);
		msg.writeByte(MSG_HELLO);
		msg.writeInt(PROTOCOL_VERSION);
		msg.writeShort(sessionIdBytes.length);
		msg.write(sessionIdBytes);
		writeFramed(out, byteArrayOutputStream.toByteArray());
	}

	private synchronized void sendIdentity(String identityHash) throws IOException
	{
		byte[] hashBytes = identityHash.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		DataOutputStream msg = new DataOutputStream(byteArrayOutputStream);
		msg.writeByte(MSG_IDENTITY);
		msg.writeShort(hashBytes.length);
		msg.write(hashBytes);
		writeFramed(out, byteArrayOutputStream.toByteArray());
	}

	private void readHelloAck() throws IOException
	{
		DataInputStream msg = readFrame();

		int type = msg.readByte() & 0xFF;

		if(type == (MSG_SERVER_ERROR & 0xFF)) {
				int errLen = msg.readUnsignedShort();
				if (errLen > 512)
				{
					throw new IOException("Server error message too long: " + errLen);
				}
				byte[] errBytes = new byte[errLen];
				msg.readFully(errBytes);
				String errorMsg = new String(errBytes, StandardCharsets.UTF_8);
				reportStatus(errorMsg);
				throw new IOException("Server rejected connection: " + errorMsg);
		} else if(type != (MSG_HELLO_ACK & 0xFF)) {
			throw new IOException("Expected HelloAck, got: 0x" + Integer.toHexString(type));
		}

		int sessionIdLen = msg.readUnsignedShort();
		if (sessionIdLen > 256)
		{
			throw new IOException("SessionId too long: " + sessionIdLen);
		}
		byte[] sessionIdBytes = new byte[sessionIdLen];
		msg.readFully(sessionIdBytes);
		sessionId = new String(sessionIdBytes, StandardCharsets.UTF_8);

		int keyLen = msg.readUnsignedShort();
		if (keyLen > 64)
		{
			throw new IOException("Key too long: " + keyLen);
		}
		byte[] key = new byte[keyLen];
		msg.readFully(key);
		currentDailyKey = key;

		int udpKeyLen = msg.readUnsignedShort();
		if (udpKeyLen > 64)
		{
			throw new IOException("UDP key too long: " + udpKeyLen);
		}
		byte[] uk = new byte[udpKeyLen];
		msg.readFully(uk);
		udpKey = uk;
	}

	private void readMessage() throws IOException
	{
		DataInputStream msg = readFrame();

		int type = msg.readByte() & 0xFF;

		switch (type)
		{
			case (MSG_KEY_ROTATION & 0xFF):
				int keyLen = msg.readUnsignedShort();
				if (keyLen > 64)
				{
					throw new IOException("Rotated key too long: " + keyLen);
				}
				byte[] newKey = new byte[keyLen];
				msg.readFully(newKey);
				currentDailyKey = newKey;
				log.debug("Key rotated");


				sendIdentity(computeIdentityHash());
				break;

			case (MSG_SERVER_ERROR & 0xFF):
				int errLen = msg.readUnsignedShort();
				if (errLen > 512)
				{
					throw new IOException("Server error message too long: " + errLen);
				}
				byte[] errBytes = new byte[errLen];
				msg.readFully(errBytes);
				String errorMsg = new String(errBytes, StandardCharsets.UTF_8);
				reportStatus(errorMsg);
				break;

			default:
				log.debug("Unknown message type: 0x{}", Integer.toHexString(type));
				break;
		}
	}

	private void startHeartbeat()
	{
		if (heartbeatThread != null)
		{
			heartbeatThread.interrupt();
		}
		heartbeatThread = new Thread(() ->
		{
			while (running.get() && isConnected())
			{
				try
				{
					Thread.sleep(HEARTBEAT_INTERVAL_MS);
					long timeSinceLastHash = System.currentTimeMillis() - lastHashListTime;
					if (timeSinceLastHash >= HEARTBEAT_INTERVAL_MS)
					{
						synchronized (this)
						{
							if (out != null)
							{
								ByteArrayOutputStream baos = new ByteArrayOutputStream();
								DataOutputStream msg = new DataOutputStream(baos);
								msg.writeByte(MSG_HASH_LIST_UPDATE);
								msg.writeShort(0);
								writeFramed(out, baos.toByteArray());
							}
						}
					}

					if (System.currentTimeMillis() - lastUdpRegisterTime >= UDP_REGISTER_INTERVAL_MS)
					{
						sendUdpRegistration();
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					break;
				}
				catch (IOException e)
				{
					log.debug("Heartbeat failed: {}", e.getMessage());
					break;
				}
			}
		}, "VoiceScape-Heartbeat");
		heartbeatThread.setDaemon(true);
		heartbeatThread.start();
	}

	private synchronized void closeSocket()
	{
		closeUdpSocket();
		try
		{
			if (socket != null)
				socket.close();
		}
		catch (IOException e)
		{
			log.debug("Error closing socket", e);
		}
		socket = null;
		out = null;
		in = null;
		playbackManager.flushLine();

	}

	private void openUdpChannel(String host, int port)
	{
		try
		{
			serverAddr = InetAddress.getByName(host);
			serverPort = port;
			udpSocket = new DatagramSocket();
			sendUdpRegistration();
			startUdpReceiveThread();
			log.debug("UDP audio channel opened to {}:{}", host, port);
		}
		catch (Exception e)
		{
			log.debug("UDP audio unavailable, using TCP fallback: {}", e.getMessage());
			closeUdpSocket();
		}
	}

	private void sendUdpRegistration()
	{
		if (udpSocket == null || udpSocket.isClosed() || sessionId == null || serverAddr == null)
		{
			return;
		}
		try
		{
			byte[] sidBytes = sessionId.getBytes(StandardCharsets.UTF_8);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream msg = new DataOutputStream(baos);
			msg.writeByte(MSG_UDP_REGISTER);
			msg.writeShort(sidBytes.length);
			msg.write(sidBytes);
			byte[] data = baos.toByteArray();
			udpSocket.send(new DatagramPacket(data, data.length, serverAddr, serverPort));
			lastUdpRegisterTime = System.currentTimeMillis();
		}
		catch (IOException e)
		{
			log.debug("UDP registration failed: {}", e.getMessage());
		}
	}

	private void startUdpReceiveThread()
	{
		udpReceiveThread = new Thread(() ->
		{
			byte[] buf = new byte[UDP_BUFFER_SIZE];
			if (udpSocket == null)
			{
				return;
			}
			while (running.get() && !udpSocket.isClosed())
			{
				try
				{
					DatagramPacket packet = new DatagramPacket(buf, buf.length);
					udpSocket.receive(packet);
					log.debug("Received UDP Packet");
					handleUdpPacket(buf, packet.getLength());
				}
				catch (SocketException e)
				{
					break;
				}
				catch (Exception e)
				{
					if (running.get())
					{
						log.debug("UDP receive error: {}", e.getMessage());
					}
				}
			}
		}, "VoiceScape-UDP-Receive");
		udpReceiveThread.setDaemon(true);
		udpReceiveThread.start();
	}

	/**
	 * Parse an inbound UDP packet from the server.
	 * Expected format: [0x12][sender_hash_len:u16][sender_hash][seq:u32][opus_payload...]
	 */
	private void handleUdpPacket(byte[] data, int length)
	{
		if (length < 1)
		{
			return;
		}
		try
		{
			DataInputStream msg = new DataInputStream(
				new java.io.ByteArrayInputStream(data, 0, length));

			int type = msg.readByte() & 0xFF;
			if (type != (MSG_AUDIO_FRAME_FROM_SERVER & 0xFF))
			{
				return;
			}

			int hashLen = msg.readUnsignedShort();
			if (hashLen > 256)
			{
				return;
			}
			byte[] senderHash = new byte[hashLen];
			msg.readFully(senderHash);
			int seq = msg.readInt();

			int payloadLen = length - 1 - 2 - hashLen - 4;
			if (payloadLen <= 0 || payloadLen > MAX_AUDIO_PAYLOAD)
			{
				return;
			}
			byte[] encrypted = new byte[payloadLen];
			msg.readFully(encrypted);

			byte[] key = udpKey;
			if (key == null)
			{
				return;
			}
			byte[] payload = UdpCrypto.decrypt(key, seq, encrypted);

			playbackManager.receiveAudio(
				new String(senderHash, StandardCharsets.UTF_8), seq, payload);
		}
		catch (Exception e)
		{
			log.debug("Malformed UDP packet: {}", e.getMessage());
		}
	}

	private void closeUdpSocket()
	{
		DatagramSocket ds = udpSocket;
		if (ds != null)
		{
			ds.close();
		}
		udpSocket = null;
		serverAddr = null;
	}
}
