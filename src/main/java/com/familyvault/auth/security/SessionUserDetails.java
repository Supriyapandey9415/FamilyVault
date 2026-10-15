package com.familyvault.auth.security;

import com.familyvault.auth.entity.User;
import java.util.List;

public final class SessionUserDetails extends org.springframework.security.core.userdetails.User {
    private final long securityVersion;

    public SessionUserDetails(User user) {
        super(user.getEmail(), user.getPassword() == null ? "" : user.getPassword(), List.of());
        securityVersion = user.getSecurityVersion();
    }

    public long getSecurityVersion() { return securityVersion; }
}
