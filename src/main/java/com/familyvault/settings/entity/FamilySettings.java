package com.familyvault.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "family_settings")
public class FamilySettings {

    @Id
    @Column(name = "family_id")
    private Long familyId;

    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(nullable = false)
    private boolean memberDocumentUploadEnabled = true;

    @Column(nullable = false)
    private boolean memberMemoryCreateEnabled = true;

    @Column(nullable = false)
    private boolean adminInvitationEnabled = true;

    @Column(nullable = false)
    private int documentExpiryReminderDays = 30;

    @Version
    @Column(nullable = false)
    private Long version;

    private Long updatedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public FamilySettings() {
    }

    public FamilySettings(Long familyId, Long updatedBy) {
        this.familyId = familyId;
        this.updatedBy = updatedBy;
    }

    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public boolean isMemberDocumentUploadEnabled() { return memberDocumentUploadEnabled; }
    public void setMemberDocumentUploadEnabled(boolean value) { this.memberDocumentUploadEnabled = value; }
    public boolean isMemberMemoryCreateEnabled() { return memberMemoryCreateEnabled; }
    public void setMemberMemoryCreateEnabled(boolean value) { this.memberMemoryCreateEnabled = value; }
    public boolean isAdminInvitationEnabled() { return adminInvitationEnabled; }
    public void setAdminInvitationEnabled(boolean value) { this.adminInvitationEnabled = value; }
    public int getDocumentExpiryReminderDays() { return documentExpiryReminderDays; }
    public void setDocumentExpiryReminderDays(int value) { this.documentExpiryReminderDays = value; }
    public Long getVersion() { return version; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
