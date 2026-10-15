package com.familyvault.notification.service;

import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.repository.FamilyRepository;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.notification.entity.NotificationType;
import com.familyvault.notification.entity.NotificationPreference;
import com.familyvault.notification.dto.NotificationResponse;
import com.familyvault.notification.entity.Notification;
import com.familyvault.notification.repository.NotificationRepository;
import com.familyvault.notification.repository.NotificationPreferenceRepository;
import com.familyvault.settings.entity.FamilyNotificationPreference;
import com.familyvault.settings.repository.FamilyNotificationPreferenceRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyRepository familyRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final FamilyNotificationPreferenceRepository familyNotificationPreferenceRepository;

    public NotificationService(NotificationRepository notificationRepository, 
                               UserRepository userRepository,
                               FamilyMemberRepository familyMemberRepository,
                               FamilyRepository familyRepository,
                               NotificationPreferenceRepository notificationPreferenceRepository,
                               FamilyNotificationPreferenceRepository familyNotificationPreferenceRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.familyRepository = familyRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.familyNotificationPreferenceRepository = familyNotificationPreferenceRepository;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));
    }

    public List<NotificationResponse> getMyNotifications(Long familyId, boolean unreadOnly) {
        User currentUser = getCurrentUser();
        List<Notification> notifications;
        if (familyId != null && unreadOnly) {
            notifications = notificationRepository.findByUserIdAndFamilyIdAndIsReadFalseOrderByCreatedAtDesc(currentUser.getId(), familyId);
        } else if (familyId != null) {
            notifications = notificationRepository.findByUserIdAndFamilyIdOrderByCreatedAtDesc(currentUser.getId(), familyId);
        } else if (unreadOnly) {
            notifications = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(currentUser.getId());
        } else {
            notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
        }

        Map<Long, String> familyNames = familyRepository.findAllById(notifications.stream()
                        .map(Notification::getFamilyId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(family -> family.getId(), family -> family.getName()));
        Map<Long, String> actorNames = userRepository.findAllById(notifications.stream()
                        .map(Notification::getActorUserId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(User::getId, this::displayName));

        return notifications.stream()
                .map(notification -> new NotificationResponse(
                        notification,
                        familyNames.get(notification.getFamilyId()),
                        actorNames.get(notification.getActorUserId())))
                .collect(Collectors.toList());
    }

    public long getUnreadCount(Long familyId) {
        User currentUser = getCurrentUser();
        if (familyId != null) {
            return notificationRepository.countByUserIdAndFamilyIdAndIsReadFalse(currentUser.getId(), familyId);
        }
        return notificationRepository.countByUserIdAndIsReadFalse(currentUser.getId());
    }

    @Transactional
    public void markAsRead(Long id) {
        User currentUser = getCurrentUser();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        
        if (!notification.getUserId().equals(currentUser.getId())) {
            throw new IllegalStateException("You do not have permission to modify this notification");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead() {
        markAllAsRead(null);
    }

    @Transactional
    public void markAllAsRead(Long familyId) {
        User currentUser = getCurrentUser();
        List<Notification> unread = familyId == null
                ? notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(currentUser.getId())
                : notificationRepository.findByUserIdAndFamilyIdAndIsReadFalseOrderByCreatedAtDesc(currentUser.getId(), familyId);
        for (Notification notif : unread) {
            notif.setIsRead(true);
        }
        notificationRepository.saveAll(unread);
    }

    @Transactional
    public void createNotification(Long userId, Long familyId, String message) {
        createNotification(userId, familyId, NotificationType.SYSTEM, "FamilyVault notification", message,
                null, null, null, null, null);
    }

    @Transactional
    public void createNotification(Long userId,
                                   Long familyId,
                                   NotificationType type,
                                   String title,
                                   String message,
                                   Long actorUserId,
                                   String entityType,
                                   Long entityId,
                                   String actionUrl,
                                   String dedupeKey) {
        if (!isEnabledForUser(userId, familyId, type)) {
            return;
        }
        if (dedupeKey != null && notificationRepository.existsByDedupeKey(dedupeKey)) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setFamilyId(familyId);
        notification.setMessage(message);
        notification.setType(type == null ? NotificationType.SYSTEM : type);
        notification.setTitle(title);
        notification.setActorUserId(actorUserId);
        notification.setEntityType(entityType);
        notification.setEntityId(entityId);
        notification.setActionUrl(actionUrl);
        notification.setDedupeKey(dedupeKey);
        notification.setIsRead(false);
        notificationRepository.save(notification);
    }

    @Transactional
    public void notifyFamilyMembers(Long familyId, Long senderUserId, String message) {
        notifyFamilyMembers(familyId, senderUserId, NotificationType.SYSTEM, "FamilyVault activity", message,
                senderUserId, null, null, "/family/" + familyId, null);
    }

    @Transactional
    public void notifyFamilyMembers(Long familyId,
                                    Long senderUserId,
                                    NotificationType type,
                                    String title,
                                    String message,
                                    Long actorUserId,
                                    String entityType,
                                    Long entityId,
                                    String actionUrl,
                                    String dedupeKey) {
        if (familyId == null) return;
        List<FamilyMember> members = familyMemberRepository.findByFamilyId(familyId);
        for (FamilyMember member : members) {
            if (member.getUserId() != null && !member.getUserId().equals(senderUserId)) {
                String recipientDedupeKey = dedupeKey == null ? null : dedupeKey + ":" + member.getUserId();
                createNotification(member.getUserId(), familyId, type, title, message, actorUserId,
                        entityType, entityId, actionUrl, recipientDedupeKey);
            }
        }
    }

    private String displayName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isBlank() ? user.getEmail() : name;
    }

    private boolean isEnabledForUser(Long userId, Long familyId, NotificationType type) {
        NotificationPreference preference = notificationPreferenceRepository.findByUserId(userId).orElse(null);
        if (preference == null || type == NotificationType.SYSTEM) {
            return true;
        }
        if (!preference.isInAppEnabled()) {
            return false;
        }
        if (type == NotificationType.FAMILY_INVITATION) {
            return preference.isInvitationEnabled();
        }

        FamilyNotificationPreference familyPreference = familyId == null ? null
                : familyNotificationPreferenceRepository.findByFamilyIdAndUserId(familyId, userId).orElse(null);
        if (familyPreference != null && familyPreference.getMutedUntil() != null
                && familyPreference.getMutedUntil().isAfter(LocalDateTime.now())) {
            return false;
        }
        if (type == NotificationType.DOCUMENT_EXPIRING) {
            return preference.isDocumentExpiryEnabled()
                    && (familyPreference == null || familyPreference.isDocumentExpiryEnabled());
        }
        return preference.isFamilyActivityEnabled()
                && (familyPreference == null || familyPreference.isFamilyActivityEnabled());
    }
}
