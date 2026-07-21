package com.example.crypt;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM helpers backed by the Bouncy Castle provider.
 */
public final class AesGcmCipher {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BITS = 256;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom;

    public AesGcmCipher(SecretKey key) {
        BouncyCastleSupport.ensureProvider();
        this.key = key;
        this.secureRandom = new SecureRandom();
    }

    public static AesGcmCipher withRandomKey() throws GeneralSecurityException {
        BouncyCastleSupport.ensureProvider();
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", BouncyCastleSupport.PROVIDER_NAME);
        keyGenerator.init(KEY_BITS);
        return new AesGcmCipher(keyGenerator.generateKey());
    }

    public static AesGcmCipher fromKeyBytes(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_BITS / 8) {
            throw new IllegalArgumentException("AES-256 key must be 32 bytes");
        }
        return new AesGcmCipher(new SecretKeySpec(keyBytes, "AES"));
    }

    public byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleSupport.PROVIDER_NAME);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        return ByteBuffer.allocate(iv.length + ciphertext.length)
                .put(iv)
                .put(ciphertext)
                .array();
    }

    public byte[] decrypt(byte[] payload) throws GeneralSecurityException {
        if (payload == null || payload.length <= IV_BYTES) {
            throw new IllegalArgumentException("ciphertext payload is too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte[] iv = new byte[IV_BYTES];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleSupport.PROVIDER_NAME);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    public String encryptToBase64(String plaintext) throws GeneralSecurityException {
        byte[] encrypted = encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(encrypted);
    }

    public String decryptFromBase64(String encoded) throws GeneralSecurityException {
        byte[] payload = java.util.Base64.getDecoder().decode(encoded);
        return new String(decrypt(payload), StandardCharsets.UTF_8);
    }
}
