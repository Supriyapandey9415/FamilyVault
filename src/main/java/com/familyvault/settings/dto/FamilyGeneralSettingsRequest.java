package com.familyvault.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FamilyGeneralSettingsRequest(
    @NotBlank(message = "Family name is required")
    @Size(max = 120, message = "Family name cannot exceed 120 characters")
    String name,

    @NotBlank(message = "Timezone is required")
    @Size(max = 64, message = "Timezone cannot exceed 64 characters")
    String timezone,

    @NotNull(message = "Settings version is required")
    Long version
) {}
