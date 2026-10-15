package com.familyvault.document.dto;

import java.time.LocalDate;

public class DocumentUpdateRequest {
    private String documentName;
    private String documentType;
    private LocalDate expiryDate;
    private Long folderId;
    private boolean hidden;
    private String password;

    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
