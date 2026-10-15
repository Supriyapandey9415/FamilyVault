package com.familyvault.settings.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.familyvault.auth.entity.AuthProvider;
import com.familyvault.auth.entity.User;
import com.familyvault.common.security.CurrentUserService;
import com.familyvault.document.repository.DocumentRepository;
import com.familyvault.family.entity.Family;
import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.entity.Role;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.family.repository.FamilyRepository;
import com.familyvault.memory.repository.MemoryRepository;
import com.familyvault.notification.entity.NotificationPreference;
import com.familyvault.notification.entity.NotificationType;
import com.familyvault.notification.repository.NotificationPreferenceRepository;
import com.familyvault.notification.service.NotificationService;
import com.familyvault.settings.dto.FamilyGeneralSettingsRequest;
import com.familyvault.settings.dto.FamilyNotificationPreferenceRequest;
import com.familyvault.settings.dto.FamilyPolicyUpdateRequest;
import com.familyvault.settings.dto.FamilySettingsResponse;
import com.familyvault.settings.dto.OwnershipTransferRequest;
import com.familyvault.settings.entity.FamilyNotificationPreference;
import com.familyvault.settings.entity.FamilySettings;
import com.familyvault.settings.entity.FamilySettingsAudit;
import com.familyvault.settings.exception.SettingsConflictException;
import com.familyvault.settings.repository.FamilyNotificationPreferenceRepository;
import com.familyvault.settings.repository.FamilySettingsAuditRepository;
import com.familyvault.settings.repository.FamilySettingsRepository;
import com.familyvault.settings.security.Capability;
import com.familyvault.settings.security.FamilyAuthorizationService;
import com.familyvault.vault.dto.VaultStatusResponse;
import com.familyvault.vault.service.VaultAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class FamilySettingsService {

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final DocumentRepository documentRepository;
    private final MemoryRepository memoryRepository;
    private final FamilySettingsRepository familySettingsRepository;
    private final FamilyNotificationPreferenceRepository familyNotificationPreferenceRepository;
    private final FamilySettingsAuditRepository auditRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final FamilyAuthorizationService authorizationService;
    private final CurrentUserService currentUserService;
    private final VaultAccessService vaultAccessService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final long vaultExpirationMillis;

    public FamilySettingsService(
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            DocumentRepository documentRepository,
            MemoryRepository memoryRepository,
            FamilySettingsRepository familySettingsRepository,
            FamilyNotificationPreferenceRepository familyNotificationPreferenceRepository,
            FamilySettingsAuditRepository auditRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            FamilyAuthorizationService authorizationService,
            CurrentUserService currentUserService,
            VaultAccessService vaultAccessService,
            NotificationService notificationService,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            @Value("${application.security.jwt.vault-expiration:600000}") long vaultExpirationMillis) {
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.documentRepository = documentRepository;
        this.memoryRepository = memoryRepository;
        this.familySettingsRepository = familySettingsRepository;
        this.familyNotificationPreferenceRepository = familyNotificationPreferenceRepository;
        this.auditRepository = auditRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.authorizationService = authorizationService;
        this.currentUserService = currentUserService;
        this.vaultAccessService = vaultAccessService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.vaultExpirationMillis = vaultExpirationMillis;
    }

    @Transactional
    public FamilySettingsResponse getSettings(Long familyId, String vaultToken) {
        User user = currentUserService.requireUser();
        FamilyMember membership = authorizationService.require(
            familyId, user.getId(), Capability.SETTINGS_VIEW);
        Family family = familyRepository.findById(familyId)
            .orElseThrow(() -> new IllegalArgumentException("Family not found"));
        FamilySettings settings = getOrCreateSettings(family, user.getId());
        NotificationPreference globalPreference = getOrCreateGlobalPreference(user.getId());
        FamilyNotificationPreference familyPreference = getOrCreateFamilyPreference(familyId, user.getId());
        VaultStatusResponse vaultStatus = vaultAccessService.getStatus(familyId, vaultToken);

        List<FamilySettingsResponse.RoleDefinition> roleCatalog = Arrays.stream(Role.values())
            .map(role -> new FamilySettingsResponse.RoleDefinition(
                role,
                authorizationService.capabilitiesForRole(role, settings)
            ))
            .toList();

        return new FamilySettingsResponse(
            new FamilySettingsResponse.FamilySummary(
                family.getId(),
                family.getName(),
                family.getCreatedAt(),
                familyMemberRepository.countByFamilyId(familyId),
                documentRepository.countByFamilyId(familyId),
                memoryRepository.countByFamilyId(familyId)
            ),
            new FamilySettingsResponse.CurrentUserAccess(
                user.getId(),
                membership.getRole(),
                authorizationService.capabilitiesForRole(membership.getRole(), settings)
            ),
            toPolicies(settings),
            toNotifications(globalPreference, familyPreference),
            new FamilySettingsResponse.SecuritySummary(
                vaultStatus.configured(),
                vaultStatus.unlocked(),
                vaultStatus.expiresAt(),
                vaultExpirationMillis / 1000
            ),
            roleCatalog,
            settings.getVersion()
        );
    }

    @Transactional
    public FamilySettingsResponse updateGeneral(
            Long familyId,
            FamilyGeneralSettingsRequest request,
            String requestId,
            String vaultToken) {
        User user = currentUserService.requireUser();
        authorizationService.require(familyId, user.getId(), Capability.FAMILY_RENAME);
        Family family = familyRepository.findById(familyId)
            .orElseThrow(() -> new IllegalArgumentException("Family not found"));
        FamilySettings settings = getOrCreateSettings(family, user.getId());
        verifyVersion(request.version(), settings.getVersion());

        String timezone = request.timezone().trim();
        try {
            ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unknown timezone: " + timezone);
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        String name = request.name().trim();
        if (!Objects.equals(family.getName(), name)) {
            changes.put("name", Map.of("from", family.getName(), "to", name));
            family.setName(name);
        }
        if (!Objects.equals(settings.getTimezone(), timezone)) {
            changes.put("timezone", Map.of("from", settings.getTimezone(), "to", timezone));
            settings.setTimezone(timezone);
        }

        if (!changes.isEmpty()) {
            settings.setUpdatedBy(user.getId());
            familyRepository.save(family);
            familySettingsRepository.saveAndFlush(settings);
            audit(familyId, user.getId(), "GENERAL_SETTINGS_UPDATED", changes, requestId);
        }
        return buildResponse(family, user, settings, vaultToken);
    }

    @Transactional
    public FamilySettingsResponse updatePolicies(
            Long familyId,
            FamilyPolicyUpdateRequest request,
            String requestId,
            String vaultToken) {
        User user = currentUserService.requireUser();
        authorizationService.require(familyId, user.getId(), Capability.SETTINGS_POLICY_UPDATE);
        Family family = familyRepository.findById(familyId)
            .orElseThrow(() -> new IllegalArgumentException("Family not found"));
        FamilySettings settings = getOrCreateSettings(family, user.getId());
        verifyVersion(request.version(), settings.getVersion());

        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.memberDocumentUploadEnabled() != null
                && request.memberDocumentUploadEnabled() != settings.isMemberDocumentUploadEnabled()) {
            changes.put("memberDocumentUploadEnabled", request.memberDocumentUploadEnabled());
            settings.setMemberDocumentUploadEnabled(request.memberDocumentUploadEnabled());
        }
        if (request.memberMemoryCreateEnabled() != null
                && request.memberMemoryCreateEnabled() != settings.isMemberMemoryCreateEnabled()) {
            changes.put("memberMemoryCreateEnabled", request.memberMemoryCreateEnabled());
            settings.setMemberMemoryCreateEnabled(request.memberMemoryCreateEnabled());
        }
        if (request.adminInvitationEnabled() != null
                && request.adminInvitationEnabled() != settings.isAdminInvitationEnabled()) {
            changes.put("adminInvitationEnabled", request.adminInvitationEnabled());
            settings.setAdminInvitationEnabled(request.adminInvitationEnabled());
        }
        if (request.documentExpiryReminderDays() != null
                && request.documentExpiryReminderDays() != settings.getDocumentExpiryReminderDays()) {
            changes.put("documentExpiryReminderDays", request.documentExpiryReminderDays());
            settings.setDocumentExpiryReminderDays(request.documentExpiryReminderDays());
        }

        if (!changes.isEmpty()) {
            settings.setUpdatedBy(user.getId());
            familySettingsRepository.saveAndFlush(settings);
            audit(familyId, user.getId(), "FAMILY_POLICIES_UPDATED", changes, requestId);
        }
        return buildResponse(family, user, settings, vaultToken);
    }

    @Transactional
    public FamilySettingsResponse updateMyNotifications(
            Long familyId,
            FamilyNotificationPreferenceRequest request,
            String requestId,
            String vaultToken) {
        User user = currentUserService.requireUser();
        authorizationService.require(familyId, user.getId(), Capability.SETTINGS_VIEW);
        Family family = familyRepository.findById(familyId)
            .orElseThrow(() -> new IllegalArgumentException("Family not found"));
        FamilySettings settings = getOrCreateSettings(family, user.getId());
        FamilyNotificationPreference preference = getOrCreateFamilyPreference(familyId, user.getId());
        verifyVersion(request.version(), preference.getVersion());

        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.familyActivityEnabled() != null
                && request.familyActivityEnabled() != preference.isFamilyActivityEnabled()) {
            changes.put("familyActivityEnabled", request.familyActivityEnabled());
            preference.setFamilyActivityEnabled(request.familyActivityEnabled());
        }
        if (request.documentExpiryEnabled() != null
                && request.documentExpiryEnabled() != preference.isDocumentExpiryEnabled()) {
            changes.put("documentExpiryEnabled", request.documentExpiryEnabled());
            preference.setDocumentExpiryEnabled(request.documentExpiryEnabled());
        }
        if (!Objects.equals(request.mutedUntil(), preference.getMutedUntil())) {
            if (request.mutedUntil() != null && !request.mutedUntil().isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("Mute-until time must be in the future");
            }
            changes.put("mutedUntil", request.mutedUntil());
            preference.setMutedUntil(request.mutedUntil());
        }

        if (!changes.isEmpty()) {
            familyNotificationPreferenceRepository.saveAndFlush(preference);
            audit(familyId, user.getId(), "MY_NOTIFICATION_PREFERENCES_UPDATED", changes, requestId);
        }
        return buildResponse(family, user, settings, vaultToken);
    }

    @Transactional
    public FamilySettingsResponse transferOwnership(
            Long familyId,
            OwnershipTransferRequest request,
            String requestId,
            String vaultToken) {
        User user = currentUserService.requireUser();
        authorizationService.require(familyId, user.getId(), Capability.OWNERSHIP_TRANSFER);
        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new IllegalStateException("Ownership transfer for social-login accounts requires re-authentication");
        }
        if (!passwordEncoder.matches(request.currentAccountPassword(), user.getPassword())) {
            throw new AccessDeniedException("Current account password is incorrect");
        }

        List<FamilyMember> lockedMembers = familyMemberRepository.findByFamilyIdForUpdate(familyId);
        FamilyMember currentOwner = lockedMembers.stream()
            .filter(member -> member.getUserId().equals(user.getId()) && member.getRole() == Role.OWNER)
            .findFirst()
            .orElseThrow(() -> new AccessDeniedException("Only the current owner can transfer ownership"));
        FamilyMember target = lockedMembers.stream()
            .filter(member -> member.getId().equals(request.targetMemberId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Target member does not belong to this family"));
        if (target.getId().equals(currentOwner.getId())) {
            throw new IllegalArgumentException("Choose another member as the new owner");
        }

        Role previousTargetRole = target.getRole();
        currentOwner.setRole(Role.ADMIN);
        familyMemberRepository.saveAndFlush(currentOwner);
        target.setRole(Role.OWNER);
        familyMemberRepository.save(target);
        audit(familyId, user.getId(), "OWNERSHIP_TRANSFERRED", Map.of(
            "fromUserId", user.getId(),
            "toUserId", target.getUserId(),
            "previousTargetRole", previousTargetRole.name()
        ), requestId);

        notificationService.createNotification(
            target.getUserId(),
            familyId,
            NotificationType.FAMILY_ROLE_CHANGED,
            "You are now the family owner",
            "Ownership of the family vault was transferred to you",
            user.getId(),
            "FAMILY_MEMBER",
            target.getId(),
            "/family/" + familyId + "/settings",
            "OWNERSHIP_TRANSFERRED:" + familyId + ":" + target.getUserId()
        );
        Family family = familyRepository.findById(familyId)
            .orElseThrow(() -> new IllegalArgumentException("Family not found"));
        FamilySettings settings = getOrCreateSettings(family, user.getId());
        return buildResponse(family, user, settings, vaultToken);
    }

    private FamilySettingsResponse buildResponse(
            Family family,
            User user,
            FamilySettings settings,
            String vaultToken) {
        Long familyId = family.getId();
        FamilyMember membership = authorizationService.require(
            familyId, user.getId(), Capability.SETTINGS_VIEW);
        NotificationPreference globalPreference = getOrCreateGlobalPreference(user.getId());
        FamilyNotificationPreference familyPreference = getOrCreateFamilyPreference(familyId, user.getId());
        VaultStatusResponse vaultStatus = vaultAccessService.getStatus(familyId, vaultToken);
        List<FamilySettingsResponse.RoleDefinition> roleCatalog = Arrays.stream(Role.values())
            .map(role -> new FamilySettingsResponse.RoleDefinition(
                role,
                authorizationService.capabilitiesForRole(role, settings)
            ))
            .toList();

        return new FamilySettingsResponse(
            new FamilySettingsResponse.FamilySummary(
                family.getId(), family.getName(), family.getCreatedAt(),
                familyMemberRepository.countByFamilyId(familyId),
                documentRepository.countByFamilyId(familyId),
                memoryRepository.countByFamilyId(familyId)
            ),
            new FamilySettingsResponse.CurrentUserAccess(
                user.getId(), membership.getRole(),
                authorizationService.capabilitiesForRole(membership.getRole(), settings)
            ),
            toPolicies(settings),
            toNotifications(globalPreference, familyPreference),
            new FamilySettingsResponse.SecuritySummary(
                vaultStatus.configured(), vaultStatus.unlocked(), vaultStatus.expiresAt(),
                vaultExpirationMillis / 1000
            ),
            roleCatalog,
            settings.getVersion()
        );
    }

    private FamilySettings getOrCreateSettings(Family family, Long actorUserId) {
        return familySettingsRepository.findById(family.getId())
            .orElseGet(() -> familySettingsRepository.saveAndFlush(
                new FamilySettings(family.getId(), actorUserId)
            ));
    }

    private NotificationPreference getOrCreateGlobalPreference(Long userId) {
        return notificationPreferenceRepository.findByUserId(userId).orElseGet(() -> {
            NotificationPreference preference = new NotificationPreference();
            preference.setUserId(userId);
            return notificationPreferenceRepository.save(preference);
        });
    }

    private FamilyNotificationPreference getOrCreateFamilyPreference(Long familyId, Long userId) {
        return familyNotificationPreferenceRepository.findByFamilyIdAndUserId(familyId, userId)
            .orElseGet(() -> {
                FamilyNotificationPreference preference = new FamilyNotificationPreference();
                preference.setFamilyId(familyId);
                preference.setUserId(userId);
                return familyNotificationPreferenceRepository.saveAndFlush(preference);
            });
    }

    private FamilySettingsResponse.FamilyPolicies toPolicies(FamilySettings settings) {
        return new FamilySettingsResponse.FamilyPolicies(
            settings.getTimezone(),
            settings.isMemberDocumentUploadEnabled(),
            settings.isMemberMemoryCreateEnabled(),
            settings.isAdminInvitationEnabled(),
            settings.getDocumentExpiryReminderDays()
        );
    }

    private FamilySettingsResponse.NotificationPreferences toNotifications(
            NotificationPreference global,
            FamilyNotificationPreference family) {
        return new FamilySettingsResponse.NotificationPreferences(
            global.isInAppEnabled(),
            global.isInvitationEnabled(),
            family.isFamilyActivityEnabled(),
            family.isDocumentExpiryEnabled(),
            family.getMutedUntil(),
            family.getVersion()
        );
    }

    private void verifyVersion(Long requestedVersion, Long currentVersion) {
        if (!Objects.equals(requestedVersion, currentVersion)) {
            throw new SettingsConflictException(
                "These settings were changed in another session. Refresh and try again."
            );
        }
    }

    private void audit(Long familyId, Long actorUserId, String action, Map<String, ?> changes, String requestId) {
        FamilySettingsAudit audit = new FamilySettingsAudit();
        audit.setFamilyId(familyId);
        audit.setActorUserId(actorUserId);
        audit.setAction(action);
        audit.setChangedFields(toJson(changes));
        audit.setRequestId(requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId);
        auditRepository.save(audit);
    }

    private String toJson(Map<String, ?> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not record the settings audit event", exception);
        }
    }
}
