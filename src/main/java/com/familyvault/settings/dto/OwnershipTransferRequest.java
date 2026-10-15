package com.familyvault.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OwnershipTransferRequest(
    @NotNull(message = "Target member is required") Long targetMemberId,
    @NotBlank(message = "Current account password is required") String currentAccountPassword
) {}
