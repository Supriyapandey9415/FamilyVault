package com.familyvault.family.service;

import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.auth.service.EmailService;
import com.familyvault.common.security.CurrentUserService;
import com.familyvault.family.dto.AcceptInvitationRequest;
import com.familyvault.family.dto.InvitationPreviewResponse;
import com.familyvault.family.dto.InvitationRequest;
import com.familyvault.family.dto.InvitationResponse;
import com.familyvault.family.dto.MemberResponse;
import com.familyvault.family.entity.Family;
import com.familyvault.family.entity.FamilyInvitation;
import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.entity.InvitationStatus;
import com.familyvault.family.entity.Role;
import com.familyvault.family.repository.FamilyInvitationRepository;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.family.repository.FamilyRepository;
import com.familyvault.notification.entity.NotificationType;
import com.familyvault.notification.service.NotificationService;
import com.familyvault.settings.security.Capability;
import com.familyvault.settings.security.FamilyAuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FamilyInvitationService {

    private static final int INVITATION_VALID_DAYS = 7;

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;
    private final FamilyAuthorizationService authorizationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public FamilyInvitationService(FamilyRepository familyRepository,
                                   FamilyMemberRepository familyMemberRepository,
                                   FamilyInvitationRepository invitationRepository,
                                   UserRepository userRepository,
                                   EmailService emailService,
                                   NotificationService notificationService,
                                   CurrentUserService currentUserService,
                                   FamilyAuthorizationService authorizationService) {
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public InvitationResponse createInvitation(Long familyId, InvitationRequest request) {
        User inviter = currentUserService.requireUser();
        FamilyMember inviterMembership = ensureCanManageInvitations(familyId, inviter);
        validateInvitationRole(request.getRole(), inviterMembership.getRole());

        String email = normalizeEmail(request.getEmail());
        if (email.equals(inviter.getEmail().trim().toLowerCase())) {
            throw new IllegalArgumentException("You are already a member of this family");
        }

        User targetUser = userRepository.findByEmail(email).orElse(null);
        if (targetUser != null && familyMemberRepository.findByFamilyIdAndUserId(familyId, targetUser.getId()).isPresent()) {
            throw new IllegalArgumentException("User is already a member of this family");
        }

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new IllegalArgumentException("Family not found"));

        invitationRepository.findByFamily_IdAndInvitedEmailAndStatus(familyId, email, InvitationStatus.PENDING)
                .ifPresent(existing -> {
                    existing.setStatus(InvitationStatus.REVOKED);
                    invitationRepository.save(existing);
                });

        return createAndSendInvitation(family, email, request.getRole(), inviter);
    }

    @Transactional
    public List<InvitationResponse> getPendingInvitations(Long familyId) {
        User currentUser = currentUserService.requireUser();
        ensureCanManageInvitations(familyId, currentUser);

        LocalDateTime now = LocalDateTime.now();
        return invitationRepository.findByFamily_IdAndStatusOrderByCreatedAtDesc(familyId, InvitationStatus.PENDING)
                .stream()
                .map(invitation -> {
                    if (invitation.getExpiresAt().isBefore(now)) {
                        invitation.setStatus(InvitationStatus.EXPIRED);
                        invitationRepository.save(invitation);
                    }
                    return invitation;
                })
                .filter(invitation -> invitation.getStatus() == InvitationStatus.PENDING)
                .map(InvitationResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public InvitationResponse resendInvitation(Long familyId, Long invitationId) {
        User inviter = currentUserService.requireUser();
        FamilyMember inviterMembership = ensureCanManageInvitations(familyId, inviter);
        FamilyInvitation oldInvitation = getInvitationForFamily(familyId, invitationId);
        validateInvitationRole(oldInvitation.getRole(), inviterMembership.getRole());

        if (oldInvitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("Only pending invitations can be resent");
        }

        oldInvitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(oldInvitation);
        return createAndSendInvitation(oldInvitation.getFamily(), oldInvitation.getInvitedEmail(), oldInvitation.getRole(), inviter);
    }

    @Transactional
    public void revokeInvitation(Long familyId, Long invitationId) {
        User currentUser = currentUserService.requireUser();
        ensureCanManageInvitations(familyId, currentUser);
        FamilyInvitation invitation = getInvitationForFamily(familyId, invitationId);
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("Only pending invitations can be revoked");
        }
        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);
    }

    @Transactional
    public InvitationPreviewResponse previewInvitation(String rawToken) {
        FamilyInvitation invitation = getByToken(rawToken);
        expireIfNecessary(invitation);
        return new InvitationPreviewResponse(invitation);
    }

    @Transactional
    public MemberResponse acceptInvitation(AcceptInvitationRequest request) {
        User currentUser = currentUserService.requireUser();
        FamilyInvitation invitation = getByToken(request.getToken());
        expireIfNecessary(invitation);

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("This invitation is no longer active");
        }

        if (!currentUser.getEmail().trim().equalsIgnoreCase(invitation.getInvitedEmail())) {
            throw new AccessDeniedException("Please login with " + invitation.getInvitedEmail() + " to accept this invitation");
        }

        Optional<FamilyMember> existingMember = familyMemberRepository.findByFamilyIdAndUserId(
                invitation.getFamily().getId(), currentUser.getId());
        if (existingMember.isPresent()) {
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitation.setAcceptedAt(LocalDateTime.now());
            invitationRepository.save(invitation);
            FamilyMember member = existingMember.get();
            return new MemberResponse(
                    member.getId(),
                    currentUser.getId(),
                    currentUser.getFirstName() + " " + currentUser.getLastName(),
                    currentUser.getEmail(),
                    member.getRole(),
                    member.getJoinedAt()
            );
        }

        FamilyMember member = new FamilyMember();
        member.setFamily(invitation.getFamily());
        member.setUserId(currentUser.getId());
        member.setRole(invitation.getRole());
        FamilyMember savedMember = familyMemberRepository.save(member);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(invitation);

        String memberName = displayName(currentUser);
        notificationService.notifyFamilyMembers(
                invitation.getFamily().getId(),
                currentUser.getId(),
                NotificationType.FAMILY_MEMBER_JOINED,
                "New family member joined",
                memberName + " joined " + invitation.getFamily().getName(),
                currentUser.getId(),
                "FAMILY_MEMBER",
                savedMember.getId(),
                "/family/" + invitation.getFamily().getId(),
                "FAMILY_MEMBER_JOINED:" + savedMember.getId()
        );

        return new MemberResponse(
                savedMember.getId(),
                currentUser.getId(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                currentUser.getEmail(),
                savedMember.getRole(),
                savedMember.getJoinedAt()
        );
    }

    private InvitationResponse createAndSendInvitation(Family family, String email, Role role, User inviter) {
        if (role == null || role == Role.OWNER) {
            throw new IllegalArgumentException("Invitation role must be MEMBER, ADMIN, or VIEWER");
        }

        String rawToken = generateToken();
        FamilyInvitation invitation = new FamilyInvitation();
        invitation.setFamily(family);
        invitation.setInvitedEmail(email);
        invitation.setRole(role);
        invitation.setTokenHash(hashToken(rawToken));
        invitation.setInvitedByUserId(inviter.getId());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(LocalDateTime.now().plusDays(INVITATION_VALID_DAYS));
        FamilyInvitation saved = invitationRepository.save(invitation);

        String invitationLink = frontendUrl.replaceAll("/$", "") + "/invite/accept?token=" + rawToken;
        emailService.sendFamilyInvitationEmail(
                email,
                family.getName(),
                inviter.getFirstName() + " " + inviter.getLastName(),
                role.name(),
                invitationLink,
                INVITATION_VALID_DAYS
        );

        userRepository.findByEmail(email).ifPresent(targetUser ->
                notificationService.createNotification(
                        targetUser.getId(),
                        family.getId(),
                        NotificationType.FAMILY_INVITATION,
                        "Family invitation",
                        inviter.getFirstName() + " invited you to join " + family.getName(),
                        inviter.getId(),
                        "INVITATION",
                        saved.getId(),
                        invitationLink,
                        "FAMILY_INVITATION:" + saved.getId() + ":" + targetUser.getId()
                )
        );

        return new InvitationResponse(saved);
    }

    private FamilyMember ensureCanManageInvitations(Long familyId, User user) {
        return authorizationService.require(familyId, user.getId(), Capability.MEMBER_INVITE);
    }

    private void validateInvitationRole(Role role, Role inviterRole) {
        if (role == null || role == Role.OWNER) {
            throw new IllegalArgumentException("Invitation role must be MEMBER, ADMIN, or VIEWER");
        }
        if (role == Role.ADMIN && inviterRole != Role.OWNER) {
            throw new AccessDeniedException("Only the OWNER can invite an ADMIN");
        }
    }

    private FamilyInvitation getInvitationForFamily(Long familyId, Long invitationId) {
        FamilyInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));
        if (!invitation.getFamily().getId().equals(familyId)) {
            throw new IllegalArgumentException("Invitation does not belong to this family");
        }
        return invitation;
    }

    private FamilyInvitation getByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Invitation token is required");
        }
        return invitationRepository.findByTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Invitation link is invalid or has expired"));
    }

    private void expireIfNecessary(FamilyInvitation invitation) {
        if (invitation.getStatus() == InvitationStatus.PENDING
                && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String displayName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isBlank() ? user.getEmail() : name;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to secure invitation token", e);
        }
    }
}
