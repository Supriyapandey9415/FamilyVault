package com.familyvault.auth.service;

import com.familyvault.auth.dto.AppleAuthSessionResponse;
import com.familyvault.auth.entity.AppleAuthSession;
import com.familyvault.auth.repository.AppleAuthSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AppleAuthSessionService {

    private static final long SESSION_MINUTES = 5;

    private final AppleAuthSessionRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AppleAuthSessionService(AppleAuthSessionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AppleAuthSessionResponse createSession() {
        LocalDateTime now = LocalDateTime.now();
        repository.deleteExpired(now);

        String state = randomUrlSafeValue();
        String nonce = randomUrlSafeValue();
        AppleAuthSession session = new AppleAuthSession();
        session.setStateHash(hashState(state));
        session.setNonce(nonce);
        session.setExpiresAt(now.plusMinutes(SESSION_MINUTES));
        repository.save(session);

        return new AppleAuthSessionResponse(
                state,
                nonce,
                Instant.now().plus(SESSION_MINUTES, ChronoUnit.MINUTES)
        );
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public String consumeNonce(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("Apple authentication state is required");
        }

        AppleAuthSession session = repository.findForUpdate(hashState(state.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Apple authentication session is invalid or already used"));
        repository.delete(session);

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Apple authentication session has expired");
        }
        return session.getNonce();
    }

    private String randomUrlSafeValue() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashState(String state) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(state.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
