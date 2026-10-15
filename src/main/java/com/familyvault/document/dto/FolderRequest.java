package com.familyvault.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FolderRequest {
    @NotBlank(message = "Folder name is required")
    @Size(max = 80, message = "Folder name must not exceed 80 characters")
    private String name;
    private boolean hidden;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
}
