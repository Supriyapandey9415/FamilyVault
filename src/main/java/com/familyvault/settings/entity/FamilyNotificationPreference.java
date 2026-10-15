package com.familyvault.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "family_notification_preferences",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_family_notification_preference",
        columnNames = {"family_id", "user_id"}
    )
)
public class FamilyNotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long familyId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private boolean familyActivityEnabled = true;

    @Column(nullable = false)
    private boolean documentExpiryEnabled = true;

    private LocalDateTime mutedUntil;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public boolean isFamilyActivityEnabled() { return familyActivityEnabled; }
    public void setFamilyActivityEnabled(boolean value) { this.familyActivityEnabled = value; }
    public boolean isDocumentExpiryEnabled() { return documentExpiryEnabled; }
    public void setDocumentExpiryEnabled(boolean value) { this.documentExpiryEnabled = value; }
    public LocalDateTime getMutedUntil() { return mutedUntil; }
    public void setMutedUntil(LocalDateTime mutedUntil) { this.mutedUntil = mutedUntil; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
