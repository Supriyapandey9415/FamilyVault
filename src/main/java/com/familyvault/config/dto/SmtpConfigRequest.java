package com.familyvault.config.dto;

import jakarta.validation.constraints.NotBlank;

public class SmtpConfigRequest {

    @NotBlank(message = "SMTP host is required")
    private String host;

    private int port = 465;

    @NotBlank(message = "SMTP username is required")
    private String username;

    private String password;

    private String fromEmail;

    private String fromName;

    public SmtpConfigRequest() {
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }
}
