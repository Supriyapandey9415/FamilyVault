package com.familyvault.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FamilyPolicyUpdateRequest(
    Boolean memberDocumentUploadEnabled,
    Boolean memberMemoryCreateEnabled,
    Boolean adminInvitationEnabled,

    @Min(value = 1, message = "Expiry reminder must be at least 1 day")
    @Max(value = 365, message = "Expiry reminder cannot exceed 365 days")
    Integer documentExpiryReminderDays,

    @NotNull(message = "Settings version is required")
    Long version
) {}
