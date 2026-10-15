package com.familyvault.document.controller;

import com.familyvault.common.response.ApiResponse;
import com.familyvault.document.dto.DocumentResponse;
import com.familyvault.document.dto.DocumentDownloadRequest;
import com.familyvault.document.dto.DocumentProtectionRequest;
import com.familyvault.document.dto.DocumentUpdateRequest;
import com.familyvault.document.entity.Document;
import com.familyvault.document.service.DocumentService;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @RequestParam("familyId") Long familyId,
            @RequestParam("documentName") String documentName,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "expiryDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(value = "protectionPassword", required = false) String protectionPassword,
            @RequestParam(value = "hidden", defaultValue = "false") boolean hidden,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken,
            @RequestParam("file") MultipartFile file) {
        try {
            DocumentResponse response = documentService.uploadDocument(familyId, documentName, documentType, expiryDate,
                    file, protectionPassword, hidden, folderId, vaultToken);
            return ResponseEntity.ok(ApiResponse.success("Document uploaded successfully", response));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to store document: " + e.getMessage()));
        }
    }

    @GetMapping("/family/{familyId}")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getDocumentsByFamily(
            @PathVariable Long familyId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken,
            @RequestParam(defaultValue = "false") boolean includeHidden) {
        try {
            List<DocumentResponse> responses = documentService.getDocumentsByFamily(familyId, includeHidden, vaultToken);
            return ResponseEntity.ok(ApiResponse.success("Documents retrieved successfully", responses));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping({"/download/{documentId}", "/{documentId}/download"})
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long documentId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken) {
        try {
            Resource file = documentService.getDocumentFile(documentId, null, vaultToken);
            Document document = documentService.getDocumentEntity(documentId, vaultToken);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .header(HttpHeaders.CONTENT_DISPOSITION, safeAttachmentHeader(document))
                    .contentType(resolveContentType(document))
                    .body(file);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/{documentId}/download", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> downloadProtectedDocument(
            @PathVariable Long documentId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken,
            @RequestBody(required = false) DocumentDownloadRequest request) {
        try {
            Resource file = documentService.getDocumentFile(documentId,
                    request == null ? null : request.getPassword(), vaultToken);
            Document document = documentService.getDocumentEntity(documentId, vaultToken);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .header(HttpHeaders.CONTENT_DISPOSITION, safeAttachmentHeader(document))
                    .contentType(resolveContentType(document))
                    .body(file);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private MediaType resolveContentType(Document document) {
        return MediaTypeFactory.getMediaType(document.getFilePath())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    private String safeAttachmentHeader(Document document) {
        return ContentDisposition.attachment()
                .filename(document.getDocumentName(), StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    @PutMapping("/{documentId}/protection")
    public ResponseEntity<ApiResponse<DocumentResponse>> updateProtection(
            @PathVariable Long documentId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken,
            @RequestBody DocumentProtectionRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Document protection updated",
                    documentService.updateProtection(documentId, request, vaultToken)));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{documentId}")
    public ResponseEntity<ApiResponse<DocumentResponse>> updateDocument(
            @PathVariable Long documentId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken,
            @RequestBody DocumentUpdateRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Document updated successfully",
                    documentService.updateDocument(documentId, request, vaultToken)));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable Long documentId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken) {
        try {
            documentService.deleteDocument(documentId, vaultToken);
            return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to delete document file: " + e.getMessage()));
        }
    }
}
