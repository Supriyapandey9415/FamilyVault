package com.familyvault.vault.dto;

public record VaultUnlockResponse(String vaultAccessToken, long expiresAt) {
}
