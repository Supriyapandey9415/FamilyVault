package com.familyvault.family.service;

import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.common.security.CurrentUserService;
import com.familyvault.family.dto.FamilyRequest;
import com.familyvault.family.dto.FamilyResponse;
import com.familyvault.family.dto.MemberRequest;
import com.familyvault.family.dto.MemberResponse;
import com.familyvault.family.entity.Family;
import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.entity.Role;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.family.repository.FamilyRepository;
import com.familyvault.notification.entity.NotificationType;
import com.familyvault.notification.service.NotificationService;
import com.familyvault.settings.entity.FamilySettings;
import com.familyvault.settings.repository.FamilySettingsRepository;
import com.familyvault.settings.security.Capability;
import com.familyvault.settings.security.FamilyAuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final FamilySettingsRepository familySettingsRepository;
    private final CurrentUserService currentUserService;
    private final FamilyAuthorizationService authorizationService;
    private final FamilyDeletionService familyDeletionService;

    public FamilyService(FamilyRepository familyRepository,
                         FamilyMemberRepository familyMemberRepository,
                         UserRepository userRepository,
                         NotificationService notificationService,
                         FamilySettingsRepository familySettingsRepository,
                         CurrentUserService currentUserService,
                         FamilyAuthorizationService authorizationService,
                         FamilyDeletionService familyDeletionService) {
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.familySettingsRepository = familySettingsRepository;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
        this.familyDeletionService = familyDeletionService;
    }

    @Transactional
    public FamilyResponse createFamily(FamilyRequest request) {
        User currentUser = currentUserService.requireUser();
        String familyName = request.getName().trim();

        if (familyRepository.existsByNameAndCreatedBy(familyName, currentUser.getId())) {
            throw new IllegalArgumentException("You have already created a family vault with this name. You can create another family name.");
        }

        Family family = new Family();
        family.setName(familyName);
        family.setCreatedBy(currentUser.getId());
        Family savedFamily = familyRepository.save(family);

        FamilyMember familyMember = new FamilyMember();
        familyMember.setFamily(savedFamily);
        familyMember.setUserId(currentUser.getId());
        familyMember.setRole(Role.OWNER);
        familyMemberRepository.save(familyMember);
        familySettingsRepository.save(new FamilySettings(savedFamily.getId(), currentUser.getId()));

        return new FamilyResponse(savedFamily);
    }

    public List<FamilyResponse> getMyFamilies() {
        User currentUser = currentUserService.requireUser();
        List<FamilyMember> memberships = familyMemberRepository.findByUserId(currentUser.getId());
        return memberships.stream()
                .map(member -> new FamilyResponse(member.getFamily()))
                .collect(Collectors.toList());
    }

    public List<MemberResponse> getMembers(Long familyId) {
        User currentUser = currentUserService.requireUser();
        authorizationService.require(familyId, currentUser.getId(), Capability.FAMILY_VIEW);
        
        List<FamilyMember> members = familyMemberRepository.findByFamilyId(familyId);
        
        return members.stream().map(member -> {
            User user = userRepository.findById(member.getUserId())
                    .orElseThrow(() -> new IllegalStateException("User not found"));
            return new MemberResponse(
                    member.getId(),
                    user.getId(),
                    user.getFirstName() + " " + user.getLastName(),
                    user.getEmail(),
                    member.getRole(),
                    member.getJoinedAt()
            );
        }).collect(Collectors.toList());
    }

    @Transactional
    public void removeMember(Long familyId, Long memberId) {
        User currentUser = currentUserService.requireUser();
        FamilyMember currentMember = authorizationService.require(
                familyId, currentUser.getId(), Capability.MEMBER_REMOVE);

        FamilyMember memberToRemove = familyMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found"));

        if (!memberToRemove.getFamily().getId().equals(familyId)) {
            throw new IllegalArgumentException("Member does not belong to this family");
        }

        if (memberToRemove.getRole() == Role.OWNER) {
            throw new AccessDeniedException("The Vault OWNER cannot be removed from the vault");
        }

        if (memberToRemove.getRole() == Role.ADMIN && currentMember.getRole() != Role.OWNER) {
            throw new AccessDeniedException("Only the OWNER can remove an ADMIN");
        }

        if (memberToRemove.getUserId().equals(currentUser.getId()) && currentMember.getRole() == Role.OWNER) {
            throw new AccessDeniedException("The Vault OWNER cannot remove themselves");
        }

        notificationService.createNotification(
                memberToRemove.getUserId(),
                familyId,
                NotificationType.FAMILY_MEMBER_REMOVED,
                "Removed from family",
                "You were removed from " + memberToRemove.getFamily().getName(),
                currentUser.getId(),
                "FAMILY_MEMBER",
                memberToRemove.getId(),
                "/",
                null
        );
        familyMemberRepository.delete(memberToRemove);
    }

    @Transactional
    public MemberResponse updateMemberRole(Long familyId, Long memberId, Role newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException("Member role is required");
        }
        if (newRole == Role.OWNER) {
            throw new IllegalArgumentException("Use the ownership transfer action to assign the OWNER role");
        }
        User currentUser = currentUserService.requireUser();
        FamilyMember currentMember = authorizationService.require(
                familyId, currentUser.getId(), Capability.MEMBER_ROLE_CHANGE);

        FamilyMember memberToUpdate = familyMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found"));

        if (!memberToUpdate.getFamily().getId().equals(familyId)) {
            throw new IllegalArgumentException("Member does not belong to this family");
        }

        if (memberToUpdate.getRole() == Role.OWNER) {
            throw new AccessDeniedException("The OWNER role can only be changed through ownership transfer");
        }

        if (currentMember.getRole() == Role.ADMIN
                && (memberToUpdate.getRole() == Role.ADMIN || newRole == Role.ADMIN)) {
            throw new AccessDeniedException("Only the OWNER can assign or modify an ADMIN role");
        }

        memberToUpdate.setRole(newRole);
        FamilyMember updated = familyMemberRepository.save(memberToUpdate);

        User user = userRepository.findById(updated.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        notificationService.createNotification(
                updated.getUserId(),
                familyId,
                NotificationType.FAMILY_ROLE_CHANGED,
                "Family role updated",
                "Your role in the family was changed to " + updated.getRole().name(),
                currentUser.getId(),
                "FAMILY_MEMBER",
                updated.getId(),
                "/family/" + familyId,
                null
        );

        return new MemberResponse(
                updated.getId(),
                user.getId(),
                user.getFirstName() + " " + user.getLastName(),
                user.getEmail(),
                updated.getRole(),
                updated.getJoinedAt()
        );
    }

    public FamilyResponse getFamilyById(Long familyId) {
        User currentUser = currentUserService.requireUser();
        FamilyMember member = authorizationService.require(
                familyId, currentUser.getId(), Capability.FAMILY_VIEW);
        return new FamilyResponse(member.getFamily());
    }

    @Transactional
    public FamilyResponse updateFamily(Long familyId, FamilyRequest request) {
        User currentUser = currentUserService.requireUser();
        FamilyMember member = authorizationService.require(
                familyId, currentUser.getId(), Capability.FAMILY_RENAME);

        String newName = request.getName().trim();
        Family family = member.getFamily();
        family.setName(newName);
        Family saved = familyRepository.save(family);
        return new FamilyResponse(saved);
    }

    @Transactional
    public void deleteFamily(Long familyId) {
        User currentUser = currentUserService.requireUser();
        familyDeletionService.deleteFamily(familyId, currentUser.getId());
    }

    @Transactional
    public void leaveFamily(Long familyId) {
        User currentUser = currentUserService.requireUser();
        FamilyMember member = authorizationService.require(
                familyId, currentUser.getId(), Capability.FAMILY_LEAVE);

        notificationService.notifyFamilyMembers(
                familyId,
                currentUser.getId(),
                NotificationType.FAMILY_MEMBER_LEFT,
                "Family member left",
                displayName(currentUser) + " left " + member.getFamily().getName(),
                currentUser.getId(),
                "FAMILY_MEMBER",
                member.getId(),
                "/family/" + familyId,
                "FAMILY_MEMBER_LEFT:" + member.getId()
        );
        familyMemberRepository.delete(member);
    }

    private String displayName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isBlank() ? user.getEmail() : name;
    }
}
