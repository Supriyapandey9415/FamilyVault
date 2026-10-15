package com.familyvault.settings.dto;

import com.familyvault.family.entity.Role;
import com.familyvault.settings.security.Capability;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record FamilySettingsResponse(
    FamilySummary family,
    CurrentUserAccess currentUser,
    FamilyPolicies policies,
    NotificationPreferences notifications,
    SecuritySummary security,
    List<RoleDefinition> roleCatalog,
    Long version
) {
    public record FamilySummary(
        Long id,
        String name,
        LocalDateTime createdAt,
        long memberCount,
        long documentCount,
        long memoryCount
    ) {}

    public record CurrentUserAccess(Long userId, Role role, Set<Capability> capabilities) {}

    public record FamilyPolicies(
        String timezone,
        boolean memberDocumentUploadEnabled,
        boolean memberMemoryCreateEnabled,
        boolean adminInvitationEnabled,
        int documentExpiryReminderDays
    ) {}

    public record NotificationPreferences(
        boolean inAppEnabled,
        boolean invitationEnabled,
        boolean familyActivityEnabled,
        boolean documentExpiryEnabled,
        LocalDateTime mutedUntil,
        Long version
    ) {}

    public record SecuritySummary(
        boolean privateVaultConfigured,
        boolean privateVaultUnlocked,
        Long privateVaultExpiresAt,
        long autoLockSeconds
    ) {}

    public record RoleDefinition(Role role, Set<Capability> capabilities) {}
}
