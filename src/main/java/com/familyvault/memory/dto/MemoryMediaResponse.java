package com.familyvault.memory.dto;

import com.familyvault.memory.entity.MemoryMedia;
import java.time.LocalDateTime;

public class MemoryMediaResponse {
    private Long id;
    private Long memoryId;
    private String fileName;
    private String fileType;
    private LocalDateTime uploadedAt;

    public MemoryMediaResponse(MemoryMedia media) {
        this.id = media.getId();
        this.memoryId = media.getMemory().getId();
        this.fileName = media.getFileName();
        this.fileType = media.getFileType();
        this.uploadedAt = media.getUploadedAt();
    }

    public Long getId() { return id; }
    public Long getMemoryId() { return memoryId; }
    public String getFileName() { return fileName; }
    public String getFileType() { return fileType; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
