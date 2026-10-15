package com.familyvault.family.service;

import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.common.security.CurrentUserService;
import com.familyvault.family.entity.Family;
import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.entity.Role;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.family.repository.FamilyRepository;
import com.familyvault.notification.service.NotificationService;
import com.familyvault.settings.repository.FamilySettingsRepository;
import com.familyvault.settings.security.Capability;
import com.familyvault.settings.security.FamilyAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyServiceRoleSecurityTests {

    @Mock private FamilyRepository familyRepository;
    @Mock private FamilyMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private FamilySettingsRepository settingsRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private FamilyAuthorizationService authorizationService;
    @Mock private FamilyDeletionService deletionService;

    private FamilyService service;
    private User actor;

    @BeforeEach
    void setUp() {
        service = new FamilyService(
            familyRepository,
            memberRepository,
            userRepository,
            notificationService,
            settingsRepository,
            currentUserService,
            authorizationService,
            deletionService
        );
        actor = new User();
        actor.setId(10L);
        actor.setEmail("admin@example.com");
    }

    @Test
    void directOwnerAssignmentIsAlwaysRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> service.updateMemberRole(7L, 22L, Role.OWNER));
        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminCannotPromoteMemberToAdmin() {
        FamilyMember admin = member(1L, 10L, Role.ADMIN);
        FamilyMember target = member(22L, 12L, Role.MEMBER);
        when(currentUserService.requireUser()).thenReturn(actor);
        when(authorizationService.require(7L, 10L, Capability.MEMBER_ROLE_CHANGE)).thenReturn(admin);
        when(memberRepository.findById(22L)).thenReturn(Optional.of(target));

        assertThrows(AccessDeniedException.class,
            () -> service.updateMemberRole(7L, 22L, Role.ADMIN));
        verify(memberRepository, never()).save(target);
    }

    private FamilyMember member(Long id, Long userId, Role role) {
        Family family = new Family();
        family.setId(7L);
        FamilyMember member = new FamilyMember();
        member.setId(id);
        member.setFamily(family);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }
}
