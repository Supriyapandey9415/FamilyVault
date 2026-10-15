package com.familyvault.document.service;

import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.document.dto.FolderRequest;
import com.familyvault.document.dto.FolderResponse;
import com.familyvault.document.entity.DocumentFolder;
import com.familyvault.document.repository.DocumentFolderRepository;
import com.familyvault.document.repository.DocumentRepository;
import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.entity.Role;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.settings.security.Capability;
import com.familyvault.settings.security.FamilyAuthorizationService;
import com.familyvault.vault.service.VaultAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentFolderService {

    private final DocumentFolderRepository folderRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final VaultAccessService vaultAccessService;
    private final FamilyAuthorizationService authorizationService;

    public DocumentFolderService(DocumentFolderRepository folderRepository,
                                 FamilyMemberRepository familyMemberRepository,
                                 UserRepository userRepository,
                                 DocumentRepository documentRepository,
                                 VaultAccessService vaultAccessService,
                                 FamilyAuthorizationService authorizationService) {
        this.folderRepository = folderRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.vaultAccessService = vaultAccessService;
        this.authorizationService = authorizationService;
    }

    public List<FolderResponse> getFolders(Long familyId) {
        return getFolders(familyId, false, null);
    }

    public List<FolderResponse> getFolders(Long familyId, boolean includeHidden, String vaultToken) {
        User user = getCurrentUser();
        FamilyMember membership = authorizationService.require(
                familyId, user.getId(), Capability.DOCUMENT_VIEW);
        boolean isOwnerOrAdmin = isOwnerOrAdmin(membership);
        if (includeHidden) {
            vaultAccessService.requireUnlocked(familyId, vaultToken);
        }
        return folderRepository.findByFamilyIdOrderByNameAsc(familyId).stream()
                .filter(folder -> !folder.isHidden()
                        || includeHidden && user.getId().equals(folder.getCreatedBy()))
                .map(FolderResponse::new)
                .toList();
    }

    @Transactional
    public FolderResponse createFolder(Long familyId, FolderRequest request) {
        return createFolder(familyId, request, null);
    }

    @Transactional
    public FolderResponse createFolder(Long familyId, FolderRequest request, String vaultToken) {
        User user = getCurrentUser();
        authorizationService.require(familyId, user.getId(), Capability.DOCUMENT_UPLOAD);
        if (request.isHidden()) {
            vaultAccessService.requireUnlocked(familyId, vaultToken);
        }
        DocumentFolder folder = new DocumentFolder();
        folder.setFamilyId(familyId);
        folder.setCreatedBy(user.getId());
        folder.setName(request.getName().trim());
        folder.setHidden(request.isHidden());
        return new FolderResponse(folderRepository.save(folder));
    }

    @Transactional
    public FolderResponse updateFolder(Long familyId, Long folderId, FolderRequest request, String vaultToken) {
        User user = getCurrentUser();
        FamilyMember membership = authorizationService.require(
                familyId, user.getId(), Capability.DOCUMENT_VIEW);
        DocumentFolder folder = folderRepository.findByIdAndFamilyId(folderId, familyId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        boolean canManage = authorizationService.has(familyId, user.getId(), Capability.DOCUMENT_EDIT_ANY)
                || user.getId().equals(folder.getCreatedBy())
                && authorizationService.has(familyId, user.getId(), Capability.DOCUMENT_EDIT_OWN);
        if (!canManage) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to update this folder");
        }
        if (folder.isHidden() && !user.getId().equals(folder.getCreatedBy())) {
            throw new org.springframework.security.access.AccessDeniedException("This personal folder belongs to another user");
        }
        if (folder.isHidden() || request.isHidden()) {
            vaultAccessService.requireUnlocked(familyId, vaultToken);
        }
        folder.setName(request.getName().trim());
        folder.setHidden(request.isHidden());
        return new FolderResponse(folderRepository.save(folder));
    }

    @Transactional
    public void deleteFolder(Long familyId, Long folderId, String vaultToken) {
        User user = getCurrentUser();
        FamilyMember membership = authorizationService.require(
                familyId, user.getId(), Capability.DOCUMENT_VIEW);
        DocumentFolder folder = folderRepository.findByIdAndFamilyId(folderId, familyId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        boolean canManage = authorizationService.has(familyId, user.getId(), Capability.DOCUMENT_DELETE_ANY)
                || user.getId().equals(folder.getCreatedBy())
                && authorizationService.has(familyId, user.getId(), Capability.DOCUMENT_DELETE_OWN);
        if (!canManage) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to delete this folder");
        }
        if (folder.isHidden() && !user.getId().equals(folder.getCreatedBy())) {
            throw new org.springframework.security.access.AccessDeniedException("This personal folder belongs to another user");
        }
        if (folder.isHidden()) {
            vaultAccessService.requireUnlocked(familyId, vaultToken);
        }
        if (documentRepository.existsByFamilyIdAndFolderId(familyId, folderId)) {
            throw new IllegalStateException("Move or delete the documents in this folder before deleting it");
        }
        folderRepository.delete(folder);
    }

    private FamilyMember verifyMembership(Long familyId, Long userId) {
        return familyMemberRepository.findByFamilyIdAndUserId(familyId, userId)
                .orElseThrow(() -> new IllegalStateException("You are not a member of this family"));
    }

    private boolean isOwnerOrAdmin(FamilyMember membership) {
        return membership.getRole() == Role.OWNER || membership.getRole() == Role.ADMIN;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated");
        }
        return userRepository.findByEmail(authentication.getName().trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));
    }
}
