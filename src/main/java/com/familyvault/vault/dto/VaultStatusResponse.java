package com.familyvault.vault.dto;

public record VaultStatusResponse(boolean configured, boolean unlocked, Long expiresAt) {
}
