package com.familyvault.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class GoogleLoginRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;

    private String inviteToken;

    public GoogleLoginRequest() {
    }

    public GoogleLoginRequest(String idToken, String inviteToken) {
        this.idToken = idToken;
        this.inviteToken = inviteToken;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getInviteToken() {
        return inviteToken;
    }

    public void setInviteToken(String inviteToken) {
        this.inviteToken = inviteToken;
    }
}
