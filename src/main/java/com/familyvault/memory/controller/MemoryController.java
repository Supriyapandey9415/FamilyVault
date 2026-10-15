package com.familyvault.memory.controller;

import com.familyvault.common.response.ApiResponse;
import com.familyvault.memory.dto.MemoryMediaResponse;
import com.familyvault.memory.dto.MemoryRequest;
import com.familyvault.memory.dto.MemoryResponse;
import com.familyvault.memory.entity.MemoryMedia;
import com.familyvault.memory.service.MemoryService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MemoryResponse>> createMemory(@Valid @RequestBody MemoryRequest request) {
        try {
            MemoryResponse response = memoryService.createMemory(request);
            return ResponseEntity.ok(ApiResponse.success("Memory created successfully", response));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/family/{familyId}")
    public ResponseEntity<ApiResponse<List<MemoryResponse>>> getMemoriesByFamily(@PathVariable Long familyId) {
        try {
            List<MemoryResponse> responses = memoryService.getMemoriesByFamily(familyId);
            return ResponseEntity.ok(ApiResponse.success("Memories retrieved successfully", responses));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MemoryResponse>> getMemoryById(@PathVariable Long id) {
        try {
            MemoryResponse response = memoryService.getMemoryById(id);
            return ResponseEntity.ok(ApiResponse.success("Memory retrieved successfully", response));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{memoryId}/media")
    public ResponseEntity<ApiResponse<MemoryMediaResponse>> uploadMedia(
            @PathVariable Long memoryId,
            @RequestParam("file") MultipartFile file) {
        try {
            MemoryMediaResponse response = memoryService.uploadMedia(memoryId, file);
            return ResponseEntity.ok(ApiResponse.success("Media uploaded successfully", response));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to store file: " + e.getMessage()));
        }
    }

    @GetMapping("/media/{mediaId}")
    public ResponseEntity<Resource> downloadMedia(@PathVariable Long mediaId) {
        try {
            Resource file = memoryService.getMediaFile(mediaId);
            MemoryMedia mediaEntity = memoryService.getMediaEntity(mediaId);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                            .filename(mediaEntity.getFileName(), StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .contentType(MediaType.parseMediaType(mediaEntity.getFileType() != null ? mediaEntity.getFileType() : "application/octet-stream"))
                    .body(file);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MemoryResponse>> updateMemory(
            @PathVariable Long id,
            @RequestBody MemoryRequest request) {
        try {
            MemoryResponse response = memoryService.updateMemory(id, request);
            return ResponseEntity.ok(ApiResponse.success("Memory updated successfully", response));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMemory(@PathVariable Long id) {
        try {
            memoryService.deleteMemory(id);
            return ResponseEntity.ok(ApiResponse.success("Memory deleted successfully", null));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
