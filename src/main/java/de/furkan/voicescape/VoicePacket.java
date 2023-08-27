package de.furkan.voicescape;

public class VoicePacket {

    byte[] audioData;
    String senderNameHashed;

    public VoicePacket(byte[] audioData, String senderNameHashed) {
        this.audioData = audioData;
        this.senderNameHashed = senderNameHashed;
    }

}
