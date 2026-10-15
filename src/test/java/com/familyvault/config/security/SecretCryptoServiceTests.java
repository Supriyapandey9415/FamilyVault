package com.familyvault.config.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretCryptoServiceTests {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsWithRandomNonceAndDecryptsRoundTrip() {
        SecretCryptoService service = new SecretCryptoService(KEY);

        String first = service.encrypt("smtp-app-password");
        String second = service.encrypt("smtp-app-password");

        assertTrue(first.startsWith("enc:v1:"));
        assertNotEquals(first, second);
        assertEquals("smtp-app-password", service.decrypt(first));
        assertEquals("smtp-app-password", service.decrypt(second));
    }

    @Test
    void supportsLegacyPlaintextReadButRequiresKeyForNewWrites() {
        SecretCryptoService service = new SecretCryptoService("");

        assertEquals("legacy-value", service.decrypt("legacy-value"));
        assertThrows(IllegalStateException.class, () -> service.encrypt("new-value"));
    }
}
