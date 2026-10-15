package com.familyvault.settings.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record FamilyNotificationPreferenceRequest(
    Boolean familyActivityEnabled,
    Boolean documentExpiryEnabled,
    LocalDateTime mutedUntil,

    @NotNull(message = "Notification preference version is required")
    Long version
) {}
