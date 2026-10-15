package com.familyvault.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private boolean inAppEnabled = true;

    @Column(nullable = false)
    private boolean familyActivityEnabled = true;

    @Column(nullable = false)
    private boolean invitationEnabled = true;

    @Column(nullable = false)
    private boolean documentExpiryEnabled = true;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public boolean isInAppEnabled() { return inAppEnabled; }
    public void setInAppEnabled(boolean inAppEnabled) { this.inAppEnabled = inAppEnabled; }
    public boolean isFamilyActivityEnabled() { return familyActivityEnabled; }
    public void setFamilyActivityEnabled(boolean familyActivityEnabled) { this.familyActivityEnabled = familyActivityEnabled; }
    public boolean isInvitationEnabled() { return invitationEnabled; }
    public void setInvitationEnabled(boolean invitationEnabled) { this.invitationEnabled = invitationEnabled; }
    public boolean isDocumentExpiryEnabled() { return documentExpiryEnabled; }
    public void setDocumentExpiryEnabled(boolean documentExpiryEnabled) { this.documentExpiryEnabled = documentExpiryEnabled; }
}
