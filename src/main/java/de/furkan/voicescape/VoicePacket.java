package de.furkan.voicescape;

import javax.sound.midi.SysexMessage;

public class VoicePacket {

    byte[] audioData;
    String senderNameHashed;

    long timeCreated;

    public VoicePacket(byte[] audioData, String senderNameHashed) {
        this.audioData = audioData;
        this.senderNameHashed = senderNameHashed;
        timeCreated = System.currentTimeMillis();
    }

}
