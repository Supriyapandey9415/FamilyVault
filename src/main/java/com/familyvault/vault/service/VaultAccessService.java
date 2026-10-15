package com.familyvault.vault.service;

import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.auth.security.JwtService;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.vault.dto.VaultPinSetupRequest;
import com.familyvault.vault.dto.VaultStatusResponse;
import com.familyvault.vault.dto.VaultUnlockRequest;
import com.familyvault.vault.dto.VaultUnlockResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VaultAccessService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long vaultExpiration;

    public VaultAccessService(UserRepository userRepository,
                              FamilyMemberRepository familyMemberRepository,
                              PasswordEncoder passwordEncoder,
                              JwtService jwtService,
                              @Value("${application.security.jwt.vault-expiration:600000}") long vaultExpiration) {
        this.userRepository = userRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.vaultExpiration = vaultExpiration;
    }

    public VaultStatusResponse getStatus(Long familyId, String vaultToken) {
        User user = getCurrentUser();
        verifyMembership(familyId, user.getId());
        boolean configured = user.getVaultPinHash() != null && !user.getVaultPinHash().isBlank();
        boolean unlocked = configured && isUnlocked(familyId, vaultToken, user);
        return new VaultStatusResponse(configured, unlocked, unlocked
                ? jwtService.extractClaim(vaultToken, io.jsonwebtoken.Claims::getExpiration).getTime() : null);
    }

    @Transactional
    public VaultUnlockResponse setupPin(VaultPinSetupRequest request) {
        User user = getCurrentUser();
        verifyMembership(request.getFamilyId(), user.getId());
        if (!passwordEncoder.matches(request.getAccountPassword(), user.getPassword())) {
            throw new AccessDeniedException("Account password is incorrect");
        }
        if (!request.getPin().equals(request.getConfirmPin())) {
            throw new IllegalArgumentException("Vault PIN and confirmation do not match");
        }
        user.setVaultPinHash(passwordEncoder.encode(request.getPin()));
        user.setVaultVersion(user.getVaultVersion() + 1);
        user.setVaultPinFailedAttempts(0);
        user.setVaultPinLockedUntil(null);
        userRepository.save(user);
        return issueToken(user, request.getFamilyId());
    }

    @Transactional(noRollbackFor = {IllegalArgumentException.class, AccessDeniedException.class})
    public VaultUnlockResponse unlock(VaultUnlockRequest request) {
        User user = getCurrentUser();
        verifyMembership(request.getFamilyId(), user.getId());
        if (user.getVaultPinHash() == null || user.getVaultPinHash().isBlank()) {
            throw new IllegalStateException("Create a Vault PIN before opening hidden content");
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getVaultPinLockedUntil() != null && user.getVaultPinLockedUntil().isAfter(now)) {
            throw new AccessDeniedException("Vault is temporarily locked after too many failed attempts");
        }
        if (user.getVaultPinLockedUntil() != null) {
            user.setVaultPinLockedUntil(null);
            user.setVaultPinFailedAttempts(0);
        }

        if (!passwordEncoder.matches(request.getPin(), user.getVaultPinHash())) {
            int failedAttempts = user.getVaultPinFailedAttempts() + 1;
            user.setVaultPinFailedAttempts(failedAttempts);
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setVaultPinLockedUntil(now.plusMinutes(LOCK_MINUTES));
                user.setVaultPinFailedAttempts(0);
            }
            userRepository.saveAndFlush(user);
            int remainingAttempts = Math.max(0, MAX_FAILED_ATTEMPTS - failedAttempts);
            throw new AccessDeniedException(remainingAttempts == 0
                    ? "Vault is locked for 15 minutes"
                    : "Incorrect Vault PIN. " + remainingAttempts + " attempts remaining");
        }

        user.setVaultPinFailedAttempts(0);
        user.setVaultPinLockedUntil(null);
        userRepository.save(user);
        return issueToken(user, request.getFamilyId());
    }

    public void requireUnlocked(Long familyId, String vaultToken) {
        User user = getCurrentUser();
        verifyMembership(familyId, user.getId());
        if (user.getVaultPinHash() == null || !isUnlocked(familyId, vaultToken, user)) {
            throw new AccessDeniedException("Vault unlock required for hidden content");
        }
    }

    private boolean isUnlocked(Long familyId, String vaultToken, User user) {
        if (vaultToken == null || vaultToken.isBlank()) {
            return false;
        }
        try {
            return jwtService.isVaultTokenValid(vaultToken, user.getEmail(), familyId,
                    user.getSecurityVersion(), user.getVaultVersion());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private VaultUnlockResponse issueToken(User user, Long familyId) {
        String token = jwtService.generateVaultToken(user.getEmail(), familyId, user.getSecurityVersion(), user.getVaultVersion());
        return new VaultUnlockResponse(token, System.currentTimeMillis() + vaultExpiration);
    }

    private void verifyMembership(Long familyId, Long userId) {
        familyMemberRepository.findByFamilyIdAndUserId(familyId, userId)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this family"));
    }

    private User getCurrentUser() {
        String email = getAuthenticatedEmail();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));
    }

    private String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }
        return authentication.getName().trim().toLowerCase();
    }
}
