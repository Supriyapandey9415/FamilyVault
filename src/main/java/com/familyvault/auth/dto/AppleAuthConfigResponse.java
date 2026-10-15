package com.familyvault.auth.dto;

public record AppleAuthConfigResponse(boolean enabled, String clientId, String redirectUri) {
}
