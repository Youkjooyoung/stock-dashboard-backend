package com.stock.dashboard.util;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AesEncryptor {

	private static final String ALGORITHM = "AES/GCM/NoPadding";
	private static final int IV_LENGTH = 12;
	private static final int TAG_LENGTH = 128;

	@Value("${encrypt.aes.key}")
	private String key;

	public String decrypt(String data) throws Exception {
		byte[] combined = Base64.getDecoder().decode(data);
		byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
		byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

		Cipher cipher = Cipher.getInstance(ALGORITHM);
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
				new GCMParameterSpec(TAG_LENGTH, iv));

		return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
	}

	public String encrypt(String data) throws Exception {
		byte[] iv = new byte[IV_LENGTH];
		new SecureRandom().nextBytes(iv);

		Cipher cipher = Cipher.getInstance(ALGORITHM);
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
				new GCMParameterSpec(TAG_LENGTH, iv));

		byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
		byte[] combined = new byte[iv.length + encrypted.length];
		System.arraycopy(iv, 0, combined, 0, iv.length);
		System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

		return Base64.getEncoder().encodeToString(combined);
	}
}