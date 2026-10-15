package com.familyvault.family.dto;

import com.familyvault.family.entity.Family;
import java.time.LocalDateTime;

public class FamilyResponse {
    private Long id;
    private String name;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public FamilyResponse(Family family) {
        this.id = family.getId();
        this.name = family.getName();
        this.createdBy = family.getCreatedBy();
        this.createdAt = family.getCreatedAt();
        this.updatedAt = family.getUpdatedAt();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
