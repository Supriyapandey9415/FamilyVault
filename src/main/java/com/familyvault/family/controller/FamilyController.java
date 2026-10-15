package com.familyvault.family.controller;

import com.familyvault.common.response.ApiResponse;
import com.familyvault.family.dto.FamilyRequest;
import com.familyvault.family.dto.FamilyResponse;
import com.familyvault.family.dto.MemberRequest;
import com.familyvault.family.dto.MemberResponse;
import com.familyvault.family.service.FamilyService;
import com.familyvault.document.dto.DocumentResponse;
import com.familyvault.document.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
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
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/families")
public class FamilyController {

    private final FamilyService familyService;
    private final DocumentService documentService;

    public FamilyController(FamilyService familyService, DocumentService documentService) {
        this.familyService = familyService;
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FamilyResponse>> createFamily(@Valid @RequestBody FamilyRequest request) {
        FamilyResponse response = familyService.createFamily(request);
        return ResponseEntity.ok(ApiResponse.success("Family created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FamilyResponse>>> getMyFamilies() {
        List<FamilyResponse> responses = familyService.getMyFamilies();
        return ResponseEntity.ok(ApiResponse.success("Families retrieved successfully", responses));
    }

    @GetMapping("/{familyId}")
    public ResponseEntity<ApiResponse<FamilyResponse>> getFamily(@PathVariable Long familyId) {
        try {
            FamilyResponse response = familyService.getFamilyById(familyId);
            return ResponseEntity.ok(ApiResponse.success("Family retrieved successfully", response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{familyId}")
    public ResponseEntity<ApiResponse<FamilyResponse>> updateFamily(
            @PathVariable Long familyId,
            @Valid @RequestBody FamilyRequest request) {
        try {
            FamilyResponse response = familyService.updateFamily(familyId, request);
            return ResponseEntity.ok(ApiResponse.success("Family updated successfully", response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{familyId}")
    public ResponseEntity<ApiResponse<Void>> deleteFamily(@PathVariable Long familyId) {
        try {
            familyService.deleteFamily(familyId);
            return ResponseEntity.ok(ApiResponse.success("Family vault deleted successfully", null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{familyId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveFamily(@PathVariable Long familyId) {
        try {
            familyService.leaveFamily(familyId);
            return ResponseEntity.ok(ApiResponse.success("Left family vault successfully", null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{familyId}/members")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> getMembers(@PathVariable Long familyId) {
        try {
            List<MemberResponse> responses = familyService.getMembers(familyId);
            return ResponseEntity.ok(ApiResponse.success("Members retrieved successfully", responses));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{familyId}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable Long familyId, @PathVariable Long memberId) {
        try {
            familyService.removeMember(familyId, memberId);
            return ResponseEntity.ok(ApiResponse.success("Member removed successfully", null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{familyId}/members/{memberId}")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMemberRole(
            @PathVariable Long familyId,
            @PathVariable Long memberId,
            @RequestBody MemberRequest request) {
        try {
            MemberResponse response = familyService.updateMemberRole(familyId, memberId, request.getRole());
            return ResponseEntity.ok(ApiResponse.success("Member role updated successfully", response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{familyId}/documents")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getFamilyDocuments(
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

    @PostMapping(value = "/{familyId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadFamilyDocument(
            @PathVariable Long familyId,
            @RequestParam("documentName") String documentName,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "expiryDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(value = "protectionPassword", required = false) String protectionPassword,
            @RequestParam(value = "hidden", defaultValue = "false") boolean hidden,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken,
            @RequestParam("file") MultipartFile file) {
        try {
            DocumentResponse response = documentService.uploadDocument(
                    familyId,
                    documentName,
                    documentType,
                    expiryDate,
                    file,
                    protectionPassword,
                    hidden,
                    folderId,
                    vaultToken
            );
            return ResponseEntity.ok(ApiResponse.success("Document uploaded successfully", response));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to store document: " + e.getMessage()));
        }
    }
}
