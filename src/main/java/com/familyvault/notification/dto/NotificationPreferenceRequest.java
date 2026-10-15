package com.familyvault.notification.dto;

public class NotificationPreferenceRequest {
    private Boolean inAppEnabled;
    private Boolean familyActivityEnabled;
    private Boolean invitationEnabled;
    private Boolean documentExpiryEnabled;

    public Boolean getInAppEnabled() { return inAppEnabled; }
    public void setInAppEnabled(Boolean inAppEnabled) { this.inAppEnabled = inAppEnabled; }
    public Boolean getFamilyActivityEnabled() { return familyActivityEnabled; }
    public void setFamilyActivityEnabled(Boolean familyActivityEnabled) { this.familyActivityEnabled = familyActivityEnabled; }
    public Boolean getInvitationEnabled() { return invitationEnabled; }
    public void setInvitationEnabled(Boolean invitationEnabled) { this.invitationEnabled = invitationEnabled; }
    public Boolean getDocumentExpiryEnabled() { return documentExpiryEnabled; }
    public void setDocumentExpiryEnabled(Boolean documentExpiryEnabled) { this.documentExpiryEnabled = documentExpiryEnabled; }
}
