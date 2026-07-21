package com.example.crypt;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Runnable Bouncy Castle sample: SHA-256 hash + AES-256-GCM round-trip.
 */
public final class CryptoSample {
    private CryptoSample() {
    }

    public static void main(String[] args) throws GeneralSecurityException {
        runDemo();
    }

    public static String runDemo() throws GeneralSecurityException {
        BouncyCastleSupport.ensureProvider();

        String provider = BouncyCastleSupport.providerInfo();
        String plaintext = "Hello Bouncy Castle!";

        MessageDigest digest = MessageDigest.getInstance("SHA-256", BouncyCastleSupport.PROVIDER_NAME);
        String hashHex = HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));

        AesGcmCipher cipher = AesGcmCipher.withRandomKey();
        String encrypted = cipher.encryptToBase64(plaintext);
        String decrypted = cipher.decryptFromBase64(encrypted);

        String report = """
                === Bouncy Castle sample ===
                provider : %s
                plaintext: %s
                sha256   : %s
                encrypted: %s
                decrypted: %s
                ok       : %s
                """.formatted(provider, plaintext, hashHex, encrypted, decrypted, plaintext.equals(decrypted));

        System.out.print(report);
        return report;
    }
}
