# VoiceScape
VoiceScape is a plugin that connects the RuneLite client to a voice server, allowing you to communicate with other players who are also using the plugin in-game. This voice chat is distance-based, so if you're not close to other players who have the plugin, the server won't send you their voice data.

## How it works
The plugin connects your client to a voice and message server on two separate threads, so your client won't experience any lag or get stuck. If the connection is successful, the client sends its local username for identification to the server. The client then listens for updates from the server about registered and unregistered players. These updates are sent by the server every 10 seconds. Once the client receives an update, it adds the registered players to a list. Every second, the client checks its surroundings for players on the list. If the client finds any registered players, it sends a JSON message to the server with their names. The server then sends sound data from those players to the client. The client can disconnect at any time and the server won't store any data.


## Here is the server that can be hosted by anyone: [VoiceScape-Server](https://github.com/derfurkan/VoiceScape-Server)
![alt text](https://i.ibb.co/bsYHxZ4/Screenshot-1.png)
![alt text](https://i.ibb.co/YPRGTgh/Screenshot-2.png)
![alt text](https://i.ibb.co/DGDLD44/Screenshot-3.png)
