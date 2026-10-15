package com.familyvault.notification.service;

import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.notification.dto.NotificationPreferenceRequest;
import com.familyvault.notification.dto.NotificationPreferenceResponse;
import com.familyvault.notification.entity.NotificationPreference;
import com.familyvault.notification.repository.NotificationPreferenceRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;
    private final UserRepository userRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional
    public NotificationPreferenceResponse getMyPreferences() {
        NotificationPreference preference = repository.findByUserId(getCurrentUser().getId())
                .orElseGet(() -> createDefaults(getCurrentUser().getId()));
        return new NotificationPreferenceResponse(preference);
    }

    @Transactional
    public NotificationPreferenceResponse updateMyPreferences(NotificationPreferenceRequest request) {
        NotificationPreference preference = repository.findByUserId(getCurrentUser().getId())
                .orElseGet(() -> createDefaults(getCurrentUser().getId()));
        if (request.getInAppEnabled() != null) preference.setInAppEnabled(request.getInAppEnabled());
        if (request.getFamilyActivityEnabled() != null) preference.setFamilyActivityEnabled(request.getFamilyActivityEnabled());
        if (request.getInvitationEnabled() != null) preference.setInvitationEnabled(request.getInvitationEnabled());
        if (request.getDocumentExpiryEnabled() != null) preference.setDocumentExpiryEnabled(request.getDocumentExpiryEnabled());
        return new NotificationPreferenceResponse(repository.save(preference));
    }

    private NotificationPreference createDefaults(Long userId) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);
        return repository.save(preference);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated");
        }
        return userRepository.findByEmail(authentication.getName().trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));
    }
}
