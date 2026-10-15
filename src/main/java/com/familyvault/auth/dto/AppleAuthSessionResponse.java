package com.familyvault.auth.dto;

import java.time.Instant;

public record AppleAuthSessionResponse(String state, String nonce, Instant expiresAt) {
}
