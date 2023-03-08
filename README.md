# VoiceScape
VoiceScape is a plugin that connects the RuneLite client to a voice server, allowing you to communicate with other players who are also using the plugin in-game. This voice chat is distance-based, so if you're not close to other players who have the plugin, the server won't send you their voice data. Latency is 500ms to 1 Second high.

## How it works
The plugin connects your client to a voice and message server on two separate threads, so your client won't experience any lag or get stuck. If the connection is successful, the client sends its hashed(encrypted) username for identification to the server. The client then listens for updates from the server about registered and unregistered players. These updates are sent by the server every X seconds. Once the client receives an update, it adds the registered players to a list. Every second, the client checks its surroundings for players on the list. If the client finds any registered players, it sends a JSON message to the server with their names. The server then sends sound data from those players to the client. The client can disconnect at any time and the server won't store any data.

# What is it NOT
VoiceScape is not a alternative to discord or other VoIP applications.
Its a fun plugin where you run random into players and can talk with them if you are woodcutting or banking etc.
If you want to fight a Boss with your clan/friends use other VoIP's like Discord instead.
<br/>
<br/>
# Will this leak my IP?
### VoiceScape will not leak your IP in any way and its not designed to do so.
The only one who can see your IP address is the one who owns the Server that you might will connect to.
<br/>
<br/>
# How can i hide my IP address?
One option to hide your IP address from the server is to tunnel it through a [Proxy](https://www.fortinet.com/resources/cyberglossary/proxy-server) or [VPN](https://nordvpn.com/de/what-is-a-vpn/).<br/>
VoiceScape has a built in Proxy function that works ONLY with [Socks5](https://www.ipvanish.com/socks5-proxy/) Proxies.<br/>
Some proxy servers do not work with VoiceScape due to restrictions.<br/>
The Username and Password field are optional and only needed if the Proxy needs authentication.
<br/>
<br/>

# How can i host a server?
### [VoiceScape-Server](https://github.com/derfurkan/VoiceScape-Server) is the open source server software used to created VoiceScape servers.
# Images
![alt text](https://i.ibb.co/m8HcSqJ/Screenshot-4.png) 
<br/>
![alt text](https://i.ibb.co/YPRGTgh/Screenshot-2.png) 
<br/>
![alt text](https://i.ibb.co/bsYHxZ4/Screenshot-1.png)
<br/>
<br/>
# Disclaimer
By using this in-game voice chat plugin to connect to servers hosted by other players, you acknowledge that while the plugin takes measures to hide your name and IP address(if using a proxy), it is still not entirely safe to join a server run by someone you do not know. There is a possibility that the server might be modified may exploit a security vulnerability in the plugin, even if the chance of this happening is very very low.

Please be aware that while the server cannot force the client to send audio packets, they can be recorded if the client does send them. Additionally, the client is protected by a safeguard that will immediately terminate the connection if the server sends unsolicited messages.

Therefore, by using this plugin to connect to a server, you do so at your own risk, and the plugin's developers and affiliates cannot be held responsible for any damages or loss of data that may occur.

