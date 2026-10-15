package com.familyvault.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "application.security.oauth2.apple")
public class AppleAuthProperties {

    private boolean enabled;
    private String clientId = "";
    private String teamId = "";
    private String keyId = "";
    private String privateKeyPath = "";
    private String privateKeyBase64 = "";
    private String redirectUri = "";

    public boolean isConfigured() {
        return enabled
                && hasText(clientId)
                && hasText(teamId)
                && hasText(keyId)
                && hasText(redirectUri)
                && hasPrivateKeySource();
    }

    private boolean hasPrivateKeySource() {
        if (hasText(privateKeyBase64)) {
            return true;
        }
        if (!hasText(privateKeyPath)) {
            return false;
        }
        try {
            return Files.isRegularFile(Path.of(privateKeyPath.trim()));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }
    public String getPrivateKeyBase64() { return privateKeyBase64; }
    public void setPrivateKeyBase64(String privateKeyBase64) { this.privateKeyBase64 = privateKeyBase64; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
}
