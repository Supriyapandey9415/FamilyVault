package com.familyvault.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class MemoryRequest {

    @NotNull(message = "Family ID is required")
    private Long familyId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;
    
    private LocalDate memoryDate;
    
    private String location;

    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getMemoryDate() { return memoryDate; }
    public void setMemoryDate(LocalDate memoryDate) { this.memoryDate = memoryDate; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
