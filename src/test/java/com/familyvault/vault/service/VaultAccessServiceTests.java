package com.familyvault.vault.service;

import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.auth.security.JwtService;
import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.vault.dto.VaultPinSetupRequest;
import com.familyvault.vault.dto.VaultUnlockRequest;
import com.familyvault.vault.dto.VaultUnlockResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class VaultAccessServiceTests {

    @Mock private UserRepository userRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private VaultAccessService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new VaultAccessService(
                userRepository,
                familyMemberRepository,
                passwordEncoder,
                jwtService,
                600_000L
        );
        user = new User();
        user.setId(12L);
        user.setEmail("member@example.com");
        user.setPassword("account-hash");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of())
        );
        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        lenient().when(familyMemberRepository.findByFamilyIdAndUserId(7L, user.getId()))
                .thenReturn(Optional.of(new FamilyMember()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsHashedPinAndReturnsShortLivedToken() {
        VaultPinSetupRequest request = new VaultPinSetupRequest();
        request.setFamilyId(7L);
        request.setAccountPassword("account-password");
        request.setPin("123456");
        request.setConfirmPin("123456");

        when(passwordEncoder.matches("account-password", "account-hash")).thenReturn(true);
        when(passwordEncoder.encode("123456")).thenReturn("pin-hash");
        when(jwtService.generateVaultToken(user.getEmail(), 7L, 0L, 1L)).thenReturn("vault-token");

        VaultUnlockResponse response = service.setupPin(request);

        assertEquals("vault-token", response.vaultAccessToken());
        assertEquals("pin-hash", user.getVaultPinHash());
        verify(userRepository).save(user);
    }

    @Test
    void rejectsWrongPinAndCountsFailedAttempt() {
        user.setVaultPinHash("pin-hash");
        VaultUnlockRequest request = new VaultUnlockRequest();
        request.setFamilyId(7L);
        request.setPin("654321");
        when(passwordEncoder.matches("654321", "pin-hash")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.unlock(request));

        assertEquals(1, user.getVaultPinFailedAttempts());
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void acceptsMatchingVaultTokenForCurrentFamily() {
        user.setVaultPinHash("pin-hash");
        when(jwtService.isVaultTokenValid("vault-token", user.getEmail(), 7L, 0L, 0L)).thenReturn(true);

        service.requireUnlocked(7L, "vault-token");

        verify(jwtService).isVaultTokenValid("vault-token", user.getEmail(), 7L, 0L, 0L);
    }
}
