# VoiceScape

  **Proximity Voicechat for OSRS**

  Hear other players when they're near you in-game.

  ## How it works

  1. The plugin opens a connection to a voice server (default: `voice-scape.de`, or your own).
  2. Every game tick, your client reports a list of nearby players **as hashed names**, not real names.
  3. The server only forwards your audio to players who are **mutually nearby** (both sides have to see each other).
  4. Audio travels over UDP, encrypted with a per-session key.

  ---

  ## Default server
  A free public server is available at **`voice-scape.de`**.
  It may or may not be running depending on when you read this.
  
  ---

  ## FAQ

  ### Will my IP be leaked to other players?
  **No.** Your IP only touches the voice server. Other players never see your address because all audio is relayed through the server.
  But whoever runs the server you connect to can see your IP. If you don't trust the operator, host your own Server.

  ### Is my RuneScape name sent to the server?
  **No.** Your Player Name is hashed with a rotating key (daily rotation on default server) on your side and then sent to the server the server can't reverse it to get your name.

  ### Why should I trust the default server (`voice-scape.de`)?
  The server does not receive your name, your account details, or your IP beyond what it needs to route audio. Alternatively you can run your own server the software is free and open-source [here](https://github.com/derfurkan/VoiceScape-Server).

  ### Is audio encrypted?
  Yes. UDP audio is encrypted with a per-session AES key.

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
