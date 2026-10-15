package com.familyvault.common.security;

import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));
    }

    public Long requireUserId() {
        return requireUser().getId();
    }
}
