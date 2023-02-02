# VoiceScape
VoiceScape is a Plugin that connects the RuneLite Client to a VoiceServer, so you can talk with other Players using the same plugin in-game. This VoiceChat is distance based, so if you are not in range of other Players using this plugin, the server won't send you any voice data from other Players.

## How it works
The Plugin connects your Client to a Voice and Message Server on two separate threads, so the client won't get stuck or lag.
If the Client connected successfully, it will send its local username of the Player that is currently logged in to the Server for identification. From now on, every second, the Client will send a JSON String over the Message Thread to the server that contains all players surrounding the local player if the surrounding has changed. The Server will then decide if the Player should receive voice packets from the players if they are also registered at the server. The Client can cut the connection at every time and the server won't store ANY data.


![alt text](https://i.ibb.co/wsG7HPd/Screenshot-1.png)
