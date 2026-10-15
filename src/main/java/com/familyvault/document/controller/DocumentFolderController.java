package com.familyvault.document.controller;

import com.familyvault.common.response.ApiResponse;
import com.familyvault.document.dto.FolderRequest;
import com.familyvault.document.dto.FolderResponse;
import com.familyvault.document.service.DocumentFolderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/families/{familyId}/folders")
public class DocumentFolderController {

    private final DocumentFolderService service;

    public DocumentFolderController(DocumentFolderService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FolderResponse>>> getFolders(
            @PathVariable Long familyId,
            @RequestParam(defaultValue = "false") boolean includeHidden,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken) {
        return ResponseEntity.ok(ApiResponse.success("Folders retrieved successfully",
                service.getFolders(familyId, includeHidden, vaultToken)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FolderResponse>> createFolder(
            @PathVariable Long familyId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken,
            @Valid @RequestBody FolderRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Folder created successfully",
                service.createFolder(familyId, request, vaultToken)));
    }

    @PutMapping("/{folderId}")
    public ResponseEntity<ApiResponse<FolderResponse>> updateFolder(
            @PathVariable Long familyId,
            @PathVariable Long folderId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken,
            @Valid @RequestBody FolderRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Folder updated successfully",
                service.updateFolder(familyId, folderId, request, vaultToken)));
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<ApiResponse<Void>> deleteFolder(
            @PathVariable Long familyId,
            @PathVariable Long folderId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken) {
        service.deleteFolder(familyId, folderId, vaultToken);
        return ResponseEntity.ok(ApiResponse.success("Folder deleted successfully", null));
    }
}
