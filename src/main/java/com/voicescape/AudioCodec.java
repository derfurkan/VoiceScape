package com.voicescape;

public class AudioCodec {

    public static int encode(short[] pcm, byte[] out) {
        int outIdx = 0;
        short lastSample = 0;

        for (short sample : pcm) {
            int delta = sample - lastSample;
            lastSample = sample;

            int n = (delta << 1) ^ (delta >> 31);

            while ((n & ~0x7F) != 0) {
                out[outIdx++] = (byte) ((n & 0x7F) | 0x80);
                n >>>= 7;
            }
            out[outIdx++] = (byte) n;
        }
        return outIdx;
    }


    public static void decode(byte[] in, int inOffset, int inLength, short[] pcm) {
        int inIdx = inOffset;
        int endIdx = inOffset + inLength;
        short lastSample = 0;
        int pcmIdx = 0;

        while (inIdx < endIdx && pcmIdx < pcm.length) {
            int n = 0;
            int shift = 0;
            byte b;
            do {
                if (inIdx >= endIdx) break;
                b = in[inIdx++];
                n |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);

            int delta = (n >>> 1) ^ -(n & 1);
            short sample = (short) (lastSample + delta);
            pcm[pcmIdx++] = sample;
            lastSample = sample;
        }
    }
}
