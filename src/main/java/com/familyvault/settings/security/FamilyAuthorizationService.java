package com.familyvault.settings.security;

import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.entity.Role;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.settings.entity.FamilySettings;
import com.familyvault.settings.repository.FamilySettingsRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Service
public class FamilyAuthorizationService {

    private final FamilyMemberRepository familyMemberRepository;
    private final FamilySettingsRepository familySettingsRepository;

    public FamilyAuthorizationService(
            FamilyMemberRepository familyMemberRepository,
            FamilySettingsRepository familySettingsRepository) {
        this.familyMemberRepository = familyMemberRepository;
        this.familySettingsRepository = familySettingsRepository;
    }

    @Transactional(readOnly = true)
    public FamilyMember requireMembership(Long familyId, Long userId) {
        return familyMemberRepository.findByFamilyIdAndUserId(familyId, userId)
            .orElseThrow(() -> new AccessDeniedException("You do not have access to this family"));
    }

    @Transactional(readOnly = true)
    public FamilyMember require(Long familyId, Long userId, Capability capability) {
        FamilyMember membership = requireMembership(familyId, userId);
        Set<Capability> capabilities = capabilitiesForRole(familyId, membership.getRole());
        if (!capabilities.contains(capability)) {
            throw new AccessDeniedException("Your family role does not allow this action");
        }
        return membership;
    }

    @Transactional(readOnly = true)
    public boolean has(Long familyId, Long userId, Capability capability) {
        return familyMemberRepository.findByFamilyIdAndUserId(familyId, userId)
            .map(member -> capabilitiesForRole(familyId, member.getRole()).contains(capability))
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public Set<Capability> capabilitiesForRole(Long familyId, Role role) {
        FamilySettings settings = familySettingsRepository.findById(familyId).orElse(null);
        return capabilitiesForRole(role, settings);
    }

    public Set<Capability> capabilitiesForRole(Role role, FamilySettings settings) {
        EnumSet<Capability> capabilities = baseCapabilities(role);

        if (role == Role.MEMBER) {
            if (settings != null && !settings.isMemberDocumentUploadEnabled()) {
                capabilities.remove(Capability.DOCUMENT_UPLOAD);
            }
            if (settings != null && !settings.isMemberMemoryCreateEnabled()) {
                capabilities.remove(Capability.MEMORY_CREATE);
            }
        }
        if (role == Role.ADMIN && settings != null && !settings.isAdminInvitationEnabled()) {
            capabilities.remove(Capability.MEMBER_INVITE);
        }

        return Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    private EnumSet<Capability> baseCapabilities(Role role) {
        return switch (role) {
            case OWNER -> {
                EnumSet<Capability> all = EnumSet.allOf(Capability.class);
                all.remove(Capability.FAMILY_LEAVE);
                yield all;
            }
            case ADMIN -> EnumSet.of(
                Capability.FAMILY_VIEW,
                Capability.FAMILY_RENAME,
                Capability.FAMILY_LEAVE,
                Capability.MEMBER_INVITE,
                Capability.MEMBER_REMOVE,
                Capability.MEMBER_ROLE_CHANGE,
                Capability.DOCUMENT_VIEW,
                Capability.DOCUMENT_UPLOAD,
                Capability.DOCUMENT_EDIT_OWN,
                Capability.DOCUMENT_EDIT_ANY,
                Capability.DOCUMENT_DELETE_OWN,
                Capability.DOCUMENT_DELETE_ANY,
                Capability.MEMORY_VIEW,
                Capability.MEMORY_CREATE,
                Capability.MEMORY_EDIT_OWN,
                Capability.MEMORY_EDIT_ANY,
                Capability.MEMORY_DELETE_OWN,
                Capability.MEMORY_DELETE_ANY,
                Capability.PRIVATE_CONTENT_ACCESS,
                Capability.SETTINGS_VIEW
            );
            case MEMBER -> EnumSet.of(
                Capability.FAMILY_VIEW,
                Capability.FAMILY_LEAVE,
                Capability.DOCUMENT_VIEW,
                Capability.DOCUMENT_UPLOAD,
                Capability.DOCUMENT_EDIT_OWN,
                Capability.DOCUMENT_DELETE_OWN,
                Capability.MEMORY_VIEW,
                Capability.MEMORY_CREATE,
                Capability.MEMORY_EDIT_OWN,
                Capability.MEMORY_DELETE_OWN,
                Capability.PRIVATE_CONTENT_ACCESS,
                Capability.SETTINGS_VIEW
            );
            case VIEWER -> EnumSet.of(
                Capability.FAMILY_VIEW,
                Capability.FAMILY_LEAVE,
                Capability.DOCUMENT_VIEW,
                Capability.MEMORY_VIEW,
                Capability.SETTINGS_VIEW
            );
        };
    }
}
