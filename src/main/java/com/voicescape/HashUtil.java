package com.voicescape;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HashUtil
{
	private static final String HMAC_ALGO = "HmacSHA256";

	public static String hmac(byte[] key, String playerName)
	{
		try
		{
			Mac mac = Mac.getInstance(HMAC_ALGO);
			mac.init(new SecretKeySpec(key, HMAC_ALGO));
			byte[] hash = mac.doFinal(playerName.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(hash);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e)
		{
			log.error("HMAC computation failed", e);
			throw new RuntimeException("HMAC computation failed", e);
		}
	}
}
