package com.voicescape;
/** Protocol v2 uses fixed-size little-endian PCM frames. */
public final class AudioCodec {
 public static int encode(short[] pcm, byte[] out) {
  if(pcm.length != 480 || out.length < 960) throw new IllegalArgumentException("Invalid PCM frame");
  for(int i=0;i<480;i++) {out[2*i]=(byte)pcm[i]; out[2*i+1]=(byte)(pcm[i]>>8);}
  return 960;
 }
 public static void decode(byte[] in,int offset,int length,short[] pcm) {
  if(length!=960 || pcm.length!=480 || offset<0 || offset>in.length-length) throw new IllegalArgumentException("Invalid PCM frame");
  for(int i=0;i<480;i++) pcm[i]=(short)((in[offset+2*i]&255)|(in[offset+2*i+1]<<8));
 }
}

