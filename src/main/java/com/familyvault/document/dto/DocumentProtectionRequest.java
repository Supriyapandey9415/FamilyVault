package com.familyvault.document.dto;

public class DocumentProtectionRequest {
    private String password;
    private boolean hidden;
    private Long folderId;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
}
