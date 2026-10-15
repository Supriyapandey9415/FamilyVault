package com.familyvault.notification.dto;

import com.familyvault.notification.entity.NotificationPreference;

public class NotificationPreferenceResponse {
    private final boolean inAppEnabled;
    private final boolean familyActivityEnabled;
    private final boolean invitationEnabled;
    private final boolean documentExpiryEnabled;

    public NotificationPreferenceResponse(NotificationPreference preference) {
        this.inAppEnabled = preference.isInAppEnabled();
        this.familyActivityEnabled = preference.isFamilyActivityEnabled();
        this.invitationEnabled = preference.isInvitationEnabled();
        this.documentExpiryEnabled = preference.isDocumentExpiryEnabled();
    }

    public boolean isInAppEnabled() { return inAppEnabled; }
    public boolean isFamilyActivityEnabled() { return familyActivityEnabled; }
    public boolean isInvitationEnabled() { return invitationEnabled; }
    public boolean isDocumentExpiryEnabled() { return documentExpiryEnabled; }
}
