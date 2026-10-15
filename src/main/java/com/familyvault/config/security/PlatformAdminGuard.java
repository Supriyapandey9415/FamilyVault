package com.familyvault.config.security;

import com.familyvault.auth.entity.User;
import com.familyvault.common.security.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PlatformAdminGuard {

    private final CurrentUserService currentUserService;
    private final Set<String> adminEmails;

    public PlatformAdminGuard(
            CurrentUserService currentUserService,
            @Value("${app.platform-admin-emails:}") List<String> adminEmails) {
        this.currentUserService = currentUserService;
        this.adminEmails = adminEmails.stream()
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    public User requirePlatformAdmin() {
        User user = currentUserService.requireUser();
        if (!user.isEmailVerified() || !adminEmails.contains(user.getEmail().trim().toLowerCase(Locale.ROOT))) {
            throw new AccessDeniedException("Platform administrator access is required");
        }
        return user;
    }
}
