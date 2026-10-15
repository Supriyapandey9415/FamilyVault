package com.familyvault.auth.dto;

public class AuthConfigResponse {

    private String googleClientId;
    private boolean googleEnabled;

    public AuthConfigResponse() {
    }

    public AuthConfigResponse(String googleClientId, boolean googleEnabled) {
        this.googleClientId = googleClientId;
        this.googleEnabled = googleEnabled;
    }

    public String getGoogleClientId() {
        return googleClientId;
    }

    public void setGoogleClientId(String googleClientId) {
        this.googleClientId = googleClientId;
    }

    public boolean isGoogleEnabled() {
        return googleEnabled;
    }

    public void setGoogleEnabled(boolean googleEnabled) {
        this.googleEnabled = googleEnabled;
    }
}
