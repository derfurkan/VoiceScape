package com.voicescape;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class UdpCrypto
{
	private UdpCrypto() {}

	public static byte[] encrypt(byte[] key, int sequenceNumber, byte[] data)
	{
		return process(key, sequenceNumber, data, Cipher.ENCRYPT_MODE);
	}

	public static byte[] decrypt(byte[] key, int sequenceNumber, byte[] data)
	{
		return process(key, sequenceNumber, data, Cipher.DECRYPT_MODE);
	}

	private static byte[] process(byte[] key, int sequenceNumber, byte[] data, int mode)
	{
		try
		{
			SecretKeySpec keySpec = new SecretKeySpec(key, 0, 16, "AES");

			byte[] iv = new byte[16];
			iv[0] = (byte) (sequenceNumber >> 24);
			iv[1] = (byte) (sequenceNumber >> 16);
			iv[2] = (byte) (sequenceNumber >> 8);
			iv[3] = (byte) sequenceNumber;

			Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
			cipher.init(mode, keySpec, new IvParameterSpec(iv));
			return cipher.doFinal(data);
		}
		catch (Exception e)
		{
			throw new RuntimeException("UDP crypto failed", e);
		}
	}
}
