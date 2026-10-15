package com.familyvault.auth.service;

import com.familyvault.auth.entity.PasswordResetToken;
import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.PasswordResetTokenRepository;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.auth.security.SecurityTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String INVALID_LINK = "This link has expired or has already been used. Please request a new one.";
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final EmailService email;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
                                PasswordEncoder encoder, EmailService email) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.email = email;
    }

    @Transactional
    public void createPasswordResetTokenAndSendEmail(String rawEmail) {
        String address = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(address).orElse(null);
        if (user == null) { return; }
        tokens.deleteByUser(user);
        String rawToken = SecurityTokens.randomToken();
        PasswordResetToken token = new PasswordResetToken(user, SecurityTokens.hash(rawToken),
                LocalDateTime.now().plusMinutes(15));
        tokens.saveAndFlush(token);
        String link = frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + rawToken;
        try {
            email.sendPasswordResetEmail(user.getEmail(), link);
        } catch (RuntimeException exception) {
            // Preserve the generic anti-enumeration response, but never leave an undelivered link valid.
            tokens.delete(token);
            log.warn("Password reset email delivery failed; outstanding token invalidated");
        }
    }

    @Transactional
    public void resetPasswordWithToken(String rawToken, String password) {
        validatePassword(password);
        String hash = tokenHash(rawToken);
        PasswordResetToken token = tokens.findByTokenForUpdate(hash)
                .orElseThrow(() -> new IllegalArgumentException(INVALID_LINK));
        if (token.isExpired() || Boolean.TRUE.equals(token.getUsed())) {
            throw new IllegalArgumentException(INVALID_LINK);
        }
        User user = token.getUser();
        user.setPassword(encoder.encode(password));
        // Possession of the emailed one-time link proves control of this mailbox.
        user.setEmailVerified(true);
        users.saveAndFlush(user);
        users.revokeSessions(user.getId());
        tokens.deleteByUser(user);
    }

    @Transactional(readOnly = true)
    public void validateTokenOnly(String rawToken) {
        PasswordResetToken token = tokens.findByToken(tokenHash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException(INVALID_LINK));
        if (token.isExpired() || Boolean.TRUE.equals(token.getUsed())) {
            throw new IllegalArgumentException(INVALID_LINK);
        }
    }

    private String tokenHash(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException(INVALID_LINK);
        }
        return SecurityTokens.hash(rawToken);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches(".*[0-9].*") || !password.matches(".*[a-zA-Z].*")) {
            throw new IllegalArgumentException("Password must contain a letter and a number, be at least 8 characters, and not exceed 72 UTF-8 bytes");
        }
    }
}
