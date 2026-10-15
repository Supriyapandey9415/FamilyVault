package com.familyvault.vault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class VaultPinSetupRequest {

    @NotNull(message = "Family ID is required")
    private Long familyId;

    @NotBlank(message = "Account password is required")
    private String accountPassword;

    @NotBlank(message = "Vault PIN is required")
    @Pattern(regexp = "\\d{6}", message = "Vault PIN must contain exactly 6 digits")
    private String pin;

    @NotBlank(message = "PIN confirmation is required")
    @Pattern(regexp = "\\d{6}", message = "PIN confirmation must contain exactly 6 digits")
    private String confirmPin;

    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public String getAccountPassword() { return accountPassword; }
    public void setAccountPassword(String accountPassword) { this.accountPassword = accountPassword; }
    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }
    public String getConfirmPin() { return confirmPin; }
    public void setConfirmPin(String confirmPin) { this.confirmPin = confirmPin; }
}
