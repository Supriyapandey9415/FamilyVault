package com.familyvault.family.dto;

import com.familyvault.family.entity.FamilyInvitation;
import com.familyvault.family.entity.InvitationStatus;
import com.familyvault.family.entity.Role;

import java.time.LocalDateTime;

public class InvitationPreviewResponse {
    private Long familyId;
    private String familyName;
    private String email;
    private Role role;
    private InvitationStatus status;
    private LocalDateTime expiresAt;

    public InvitationPreviewResponse(FamilyInvitation invitation) {
        this.familyId = invitation.getFamily().getId();
        this.familyName = invitation.getFamily().getName();
        this.email = invitation.getInvitedEmail();
        this.role = invitation.getRole();
        this.status = invitation.getStatus();
        this.expiresAt = invitation.getExpiresAt();
    }

    public Long getFamilyId() { return familyId; }
    public String getFamilyName() { return familyName; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public InvitationStatus getStatus() { return status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
