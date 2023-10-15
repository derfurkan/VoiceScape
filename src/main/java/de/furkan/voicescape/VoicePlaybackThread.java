package de.furkan.voicescape;

import javax.sound.sampled.*;
import java.util.HashMap;

public class VoicePlaybackThread extends Thread {
    public final Thread playbackThread;
    public final HashMap<byte[], Float> audioDataList = new HashMap<>();

    private final String identifier;

    public VoiceScapePlugin voiceScapePlugin;
    Clip currentClip;

    public VoicePlaybackThread(VoiceScapePlugin voiceScapePlugin, String identifier) {
        playbackThread = new Thread(this, "VoicePlaybackThread");
        this.voiceScapePlugin = voiceScapePlugin;
        this.identifier = identifier;
        playbackThread.start();
    }

    AudioFormat getAudioFormat() {
        return new AudioFormat(44100.0f, 16, 1, true, false);
    }


    public void playAudio(byte[] audioData, float volume) {
        try {
            currentClip = AudioSystem.getClip();
            currentClip.open(getAudioFormat(), audioData, 0, audioData.length);
            FloatControl volumeControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);
            volumeControl.setValue(20f * (float) Math.log10(volume));
            currentClip.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            SourceDataLine line = AudioSystem.getSourceDataLine(getAudioFormat());
            line.open(getAudioFormat());
            line.start();
            while (playbackThread != null && !playbackThread.isInterrupted() && voiceScapePlugin.voiceEngine != null && voiceScapePlugin.registeredPlayers.contains(identifier)) {
                if (voiceScapePlugin.config.altPlay())
                    continue;
                new HashMap<>(audioDataList).forEach((bytes, aFloat) -> {
                    FloatControl volumeControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    volumeControl.setValue(20f * (float) Math.log10(aFloat));
                    line.write(bytes, 0, bytes.length);
                    audioDataList.remove(bytes);
                });
            }
            audioDataList.clear();
            line.drain();
            line.close();
            voiceScapePlugin.playbackThreads.remove(identifier);
            playbackThread.interrupt();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }
}
