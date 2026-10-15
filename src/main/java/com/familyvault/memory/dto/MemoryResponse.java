package com.familyvault.memory.dto;

import com.familyvault.memory.entity.Memory;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class MemoryResponse {
    private Long id;
    private Long familyId;
    private String title;
    private String description;
    private LocalDate memoryDate;
    private String location;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String fileUrl;

    public MemoryResponse(Memory memory) {
        this.id = memory.getId();
        this.familyId = memory.getFamilyId();
        this.title = memory.getTitle();
        this.description = memory.getDescription();
        this.memoryDate = memory.getMemoryDate();
        this.location = memory.getLocation();
        this.createdBy = memory.getCreatedBy();
        this.createdAt = memory.getCreatedAt();
        this.updatedAt = memory.getUpdatedAt();
    }

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getMemoryDate() { return memoryDate; }
    public String getLocation() { return location; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
}
