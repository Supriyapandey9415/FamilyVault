package com.familyvault.settings.security;

import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.entity.Role;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.settings.entity.FamilySettings;
import com.familyvault.settings.repository.FamilySettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyAuthorizationServiceTests {

    @Mock private FamilyMemberRepository memberRepository;
    @Mock private FamilySettingsRepository settingsRepository;

    private FamilyAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new FamilyAuthorizationService(memberRepository, settingsRepository);
    }

    @Test
    void ownerHasPolicyAndDeletionCapabilitiesButCannotLeave() {
        when(settingsRepository.findById(7L)).thenReturn(Optional.empty());

        var capabilities = service.capabilitiesForRole(7L, Role.OWNER);

        assertTrue(capabilities.contains(Capability.FAMILY_DELETE));
        assertTrue(capabilities.contains(Capability.SETTINGS_POLICY_UPDATE));
        assertFalse(capabilities.contains(Capability.FAMILY_LEAVE));
    }

    @Test
    void familyPolicyCanDisableMemberWrites() {
        FamilySettings settings = new FamilySettings(7L, 1L);
        settings.setMemberDocumentUploadEnabled(false);
        settings.setMemberMemoryCreateEnabled(false);
        when(settingsRepository.findById(7L)).thenReturn(Optional.of(settings));

        var capabilities = service.capabilitiesForRole(7L, Role.MEMBER);

        assertFalse(capabilities.contains(Capability.DOCUMENT_UPLOAD));
        assertFalse(capabilities.contains(Capability.MEMORY_CREATE));
        assertTrue(capabilities.contains(Capability.DOCUMENT_VIEW));
    }

    @Test
    void viewerCannotPassDocumentUploadAuthorization() {
        FamilyMember viewer = new FamilyMember();
        viewer.setRole(Role.VIEWER);
        viewer.setUserId(12L);
        when(memberRepository.findByFamilyIdAndUserId(7L, 12L)).thenReturn(Optional.of(viewer));
        when(settingsRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
            () -> service.require(7L, 12L, Capability.DOCUMENT_UPLOAD));
    }
}
