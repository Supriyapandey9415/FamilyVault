package com.familyvault.config.dto;

import com.familyvault.config.entity.SmtpConfig;

import java.time.LocalDateTime;

public class SmtpConfigResponse {

    private Long id;
    private String host;
    private int port;
    private String username;
    private String fromEmail;
    private String fromName;
    private boolean passwordConfigured;
    private boolean active;
    private LocalDateTime updatedAt;

    public SmtpConfigResponse() {
    }

    public SmtpConfigResponse(SmtpConfig config) {
        if (config != null) {
            this.id = config.getId();
            this.host = config.getHost();
            this.port = config.getPort();
            this.username = config.getUsername();
            this.fromEmail = config.getFromEmail();
            this.fromName = config.getFromName();
            this.passwordConfigured = config.getPassword() != null && !config.getPassword().isBlank();
            this.active = config.isActive();
            this.updatedAt = config.getUpdatedAt();
        }
    }

    public Long getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public boolean isPasswordConfigured() {
        return passwordConfigured;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
