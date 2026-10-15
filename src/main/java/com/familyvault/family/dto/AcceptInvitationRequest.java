package com.familyvault.family.dto;

import jakarta.validation.constraints.NotBlank;

public class AcceptInvitationRequest {

    @NotBlank(message = "Invitation token is required")
    private String token;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
