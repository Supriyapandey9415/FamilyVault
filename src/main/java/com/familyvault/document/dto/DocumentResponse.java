package com.familyvault.document.dto;

import com.familyvault.document.entity.Document;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class DocumentResponse {
    private Long id;
    private Long familyId;
    private Long uploadedBy;
    private String documentName;
    private String documentType;
    private LocalDate expiryDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long folderId;
    private boolean hidden;
    private boolean passwordProtected;

    private String uploadedByName;

    public DocumentResponse(Document document) {
        this.id = document.getId();
        this.familyId = document.getFamilyId();
        this.uploadedBy = document.getUploadedBy();
        this.documentName = document.getDocumentName();
        this.documentType = document.getDocumentType();
        this.expiryDate = document.getExpiryDate();
        this.createdAt = document.getCreatedAt();
        this.updatedAt = document.getUpdatedAt();
        this.folderId = document.getFolderId();
        this.hidden = document.isHidden();
        this.passwordProtected = document.isPasswordProtected();
    }

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public Long getUploadedBy() { return uploadedBy; }
    public String getUploadedByName() { return uploadedByName; }
    public void setUploadedByName(String uploadedByName) { this.uploadedByName = uploadedByName; }
    public String getDocumentName() { return documentName; }
    public String getDocumentType() { return documentType; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getFolderId() { return folderId; }
    public boolean isHidden() { return hidden; }
    public boolean isPasswordProtected() { return passwordProtected; }
}
