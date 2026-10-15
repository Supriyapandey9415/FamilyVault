package com.familyvault.auth.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppleAuthPropertiesTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRemainDisabledWithoutCredentials() {
        AppleAuthProperties properties = new AppleAuthProperties();
        assertFalse(properties.isConfigured());
    }

    @Test
    void shouldEnableOnlyWhenEveryRequiredValueAndKeyArePresent() throws Exception {
        Path key = tempDir.resolve("AuthKey_TEST.p8");
        Files.writeString(key, "test-key");

        AppleAuthProperties properties = new AppleAuthProperties();
        properties.setEnabled(true);
        properties.setClientId("com.familyvault.web");
        properties.setTeamId("TEAM123");
        properties.setKeyId("KEY123");
        properties.setPrivateKeyPath(key.toString());
        properties.setRedirectUri("https://auth.example.com/apple/callback");

        assertTrue(properties.isConfigured());
    }
}
