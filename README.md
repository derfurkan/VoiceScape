# VoiceScape

  **Proximity Voicechat for OSRS**

  Hear other players when they're near you in-game.

  ## How it works

  1. The plugin opens a connection to a voice server (default: `voice-scape.de`, or your own).
  2. Every game tick, your client reports a list of nearby players **as hashed names**, not real names.
  3. The server only forwards your audio to players who are **mutually nearby** (both sides have to see each other).

  ---

  ## Default server
  A free public server is available at **`voice-scape.de`**.
  It may or may not be running depending on when you read this.

  ---

  ## FAQ

  ### Will my IP be leaked to other players? (P2P?)
  **No.** Your IP only touches the voice server. Other players never see your address because all audio is relayed through the server.
  But whoever runs the server you connect to can see your IP (Most people can change their IP by restarting the Router if the ISP offers dynamic IPs).
  To prevent this you can tunnel your Connection through a VPN but make sure it supports Port Forwarding.

  ### Is my RuneScape name sent to the server?
  **No.** Your Player Name is hashed with a rotating key (daily rotation on default server) on your side and then sent to the server the server can test candidate names against these hashes.

  ### Is audio encrypted?
  **Yes.** UDP audio is encrypted with a per-session AES key.

  ### I'm having audio issues.
  - Make sure the right input/output device is selected in the panel.
  - Try toggling **Local loopback** to confirm your mic is being picked up.
  - Check you're not accidentally deafened or muted in the panel.
  - Make sure your Router or VPN supports UDP at Port 5555 (or whatever the servers port is).

  ---

  ## Self-hosting

  The server is a separate project: [VoiceScape-Server](https://github.com/derfurkan/VoiceScape-Server).

  ---

  ## Support / Donate
  [Ko-Fi](https://ko-fi.com/derfurkan)

## Protocol v2 migration and security

Deploy the v2 client and server together; v1 is incompatible. TCP requires TLS with a certificate trusted by the client JVM and matching the configured hostname. Start the server with `-Dtls_cert=/path/fullchain.pem -Dtls_key=/path/private-key.pem` before `-jar`. There is no insecure fallback. Session resumption is disabled. Audio uses authenticated AES-GCM with random packet nonces and fixed 24 kHz / 16-bit mono PCM (960 bytes per 20 ms frame).

Player-name hashes are pseudonyms, **not verified account identities**: every connected client receives the daily HMAC key and can claim an unoccupied name. Neither proximity nor ownership of an OSRS account can be securely established from client-supplied hashes. Do not treat this service as a private or authenticated conversation channel. A future account-verification service is needed for that guarantee. The relay can decrypt audio and can identify guessed player names; this is not end-to-end encryption.

