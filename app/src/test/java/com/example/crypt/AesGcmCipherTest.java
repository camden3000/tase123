package com.example.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import org.junit.jupiter.api.Test;

class AesGcmCipherTest {
    @Test
    void encryptDecryptRoundTrip() throws GeneralSecurityException {
        AesGcmCipher cipher = AesGcmCipher.withRandomKey();
        String plaintext = "secret message";

        String encrypted = cipher.encryptToBase64(plaintext);
        String decrypted = cipher.decryptFromBase64(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void sampleDemoSucceeds() throws GeneralSecurityException {
        String report = CryptoSample.runDemo();
        assertTrue(report.contains("ok       : true"));
        assertTrue(report.contains("BC "));
    }
}
