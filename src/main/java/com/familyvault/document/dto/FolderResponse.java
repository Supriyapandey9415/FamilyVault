package com.familyvault.document.dto;

import com.familyvault.document.entity.DocumentFolder;

public class FolderResponse {
    private final Long id;
    private final Long familyId;
    private final String name;
    private final boolean hidden;
    private final Long createdBy;

    public FolderResponse(DocumentFolder folder) {
        this.id = folder.getId();
        this.familyId = folder.getFamilyId();
        this.name = folder.getName();
        this.hidden = folder.isHidden();
        this.createdBy = folder.getCreatedBy();
    }

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public String getName() { return name; }
    public boolean isHidden() { return hidden; }
    public Long getCreatedBy() { return createdBy; }
}
