package com.familyvault.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class AppleLoginRequest {

    @NotBlank(message = "Apple authorization code is required")
    private String authorizationCode;

    @NotBlank(message = "Apple authentication state is required")
    private String state;

    private String firstName;
    private String lastName;
    private String inviteToken;

    public String getAuthorizationCode() { return authorizationCode; }
    public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getInviteToken() { return inviteToken; }
    public void setInviteToken(String inviteToken) { this.inviteToken = inviteToken; }
}
