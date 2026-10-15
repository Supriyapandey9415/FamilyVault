package com.familyvault.vault.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class VaultUnlockRequest {

    @NotNull(message = "Family ID is required")
    private Long familyId;

    @NotBlank(message = "Vault PIN is required")
    @Pattern(regexp = "\\d{6}", message = "Vault PIN must contain exactly 6 digits")
    private String pin;

    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }
}
