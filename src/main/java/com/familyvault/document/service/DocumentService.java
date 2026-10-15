package com.familyvault.document.service;

import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.document.dto.DocumentResponse;
import com.familyvault.document.entity.Document;
import com.familyvault.document.entity.DocumentFolder;
import com.familyvault.document.repository.DocumentRepository;
import com.familyvault.document.repository.DocumentFolderRepository;
import com.familyvault.document.dto.DocumentProtectionRequest;
import com.familyvault.document.dto.DocumentUpdateRequest;
import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.entity.Role;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.notification.service.NotificationService;
import com.familyvault.notification.entity.NotificationType;
import com.familyvault.settings.security.Capability;
import com.familyvault.settings.security.FamilyAuthorizationService;
import com.familyvault.storage.FileStorageService;
import com.familyvault.storage.StorageArea;
import com.familyvault.vault.service.VaultAccessService;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final DocumentFolderRepository folderRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final VaultAccessService vaultAccessService;
    private final FamilyAuthorizationService authorizationService;
    
    private static final long MAX_DOCUMENT_SIZE = 25L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "csv", "txt",
            "jpg", "jpeg", "png", "webp"
    );

    public DocumentService(DocumentRepository documentRepository, 
                           FamilyMemberRepository familyMemberRepository, 
                           UserRepository userRepository,
                           NotificationService notificationService,
                           DocumentFolderRepository folderRepository,
                           PasswordEncoder passwordEncoder,
                           FileStorageService fileStorageService,
                           VaultAccessService vaultAccessService,
                           FamilyAuthorizationService authorizationService) {
        this.documentRepository = documentRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.folderRepository = folderRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.vaultAccessService = vaultAccessService;
        this.authorizationService = authorizationService;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));
    }

    private FamilyMember verifyFamilyMembership(Long familyId, Long userId) {
        return familyMemberRepository.findByFamilyIdAndUserId(familyId, userId)
                .orElseThrow(() -> new IllegalStateException("You are not a member of this family"));
    }

    private boolean isOwnerOrAdmin(FamilyMember membership) {
        return membership.getRole() == Role.OWNER || membership.getRole() == Role.ADMIN;
    }

    private boolean canManageOwnDocument(FamilyMember membership, Document document, Long userId) {
        return membership.getRole() != Role.VIEWER
                && document.getUploadedBy() != null
                && document.getUploadedBy().equals(userId);
    }

    private boolean canAccessHiddenDocument(Document document,
                                            DocumentFolder folder,
                                            FamilyMember membership,
                                            Long userId) {
        boolean hiddenByDocument = document.isHidden();
        boolean hiddenByFolder = folder != null && folder.isHidden();
        if (!hiddenByDocument && !hiddenByFolder) {
            return true;
        }
        // Personal private content belongs to its uploader, including when the caller is an admin.
        return document.getUploadedBy() != null && document.getUploadedBy().equals(userId);
    }

    private DocumentFolder getFolder(Long folderId, Long familyId) {
        return folderId == null ? null : folderRepository.findByIdAndFamilyId(folderId, familyId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Document folder is not available in this family"));
    }

    private void verifyHiddenDocumentAccess(Document document, FamilyMember membership, Long userId) {
        if (!canAccessHiddenDocument(document, getFolder(document.getFolderId(), document.getFamilyId()), membership, userId)) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to access this hidden document");
        }
    }

    private void verifyFolderAccess(DocumentFolder folder, FamilyMember membership, Long userId) {
        if (folder.isHidden()
                && (folder.getCreatedBy() == null || !folder.getCreatedBy().equals(userId))) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to use this hidden folder");
        }
    }

    private String validateUpload(String documentName, MultipartFile file) {
        normalizeDocumentName(documentName);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty document file is required");
        }
        if (file.getSize() > MAX_DOCUMENT_SIZE) {
            throw new IllegalArgumentException("Document size must not exceed 25MB");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("Document filename is required");
        }
        int extensionIndex = originalFileName.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex == originalFileName.length() - 1) {
            throw new IllegalArgumentException("Document file type is not supported");
        }
        String extension = originalFileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Document file type is not supported");
        }
        com.familyvault.storage.UploadContentValidator.validate(file, extension);
        return extension;
    }

    private String normalizeDocumentName(String documentName) {
        if (documentName == null || documentName.isBlank()) {
            throw new IllegalArgumentException("Document name is required");
        }
        String normalizedName = documentName.trim();
        if (normalizedName.length() > 255 || normalizedName.indexOf('\r') >= 0 || normalizedName.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Document name is invalid");
        }
        return normalizedName;
    }

    @Transactional
    public DocumentResponse uploadDocument(Long familyId, String documentName, String documentType, LocalDate expiryDate, MultipartFile file) throws IOException {
        return uploadDocument(familyId, documentName, documentType, expiryDate, file, null, false, null);
    }

    @Transactional
    public DocumentResponse uploadDocument(Long familyId,
                                           String documentName,
                                           String documentType,
                                           LocalDate expiryDate,
                                           MultipartFile file,
                                           String protectionPassword,
                                           boolean hidden,
                                           Long folderId) throws IOException {
        return uploadDocument(familyId, documentName, documentType, expiryDate, file,
                protectionPassword, hidden, folderId, null);
    }

    @Transactional
    public DocumentResponse uploadDocument(Long familyId,
                                           String documentName,
                                           String documentType,
                                           LocalDate expiryDate,
                                           MultipartFile file,
                                           String protectionPassword,
                                           boolean hidden,
                                           Long folderId,
                                           String vaultToken) throws IOException {
        User currentUser = getCurrentUser();
        FamilyMember membership = authorizationService.require(
                familyId, currentUser.getId(), Capability.DOCUMENT_UPLOAD);

        String fileExtension = validateUpload(documentName, file);
        if (protectionPassword != null && !protectionPassword.isBlank() && protectionPassword.length() < 6) {
            throw new IllegalArgumentException("Document password must be at least 6 characters");
        }

        DocumentFolder targetFolder = null;
        if (folderId != null) {
            targetFolder = folderRepository.findByIdAndFamilyId(folderId, familyId)
                    .orElseThrow(() -> new IllegalArgumentException("Folder does not belong to this family"));
            verifyFolderAccess(targetFolder, membership, currentUser.getId());
        }
        if (hidden || targetFolder != null && targetFolder.isHidden()) {
            vaultAccessService.requireUnlocked(familyId, vaultToken);
        }

        String storageKey;
        try (var inputStream = file.getInputStream()) {
            storageKey = fileStorageService.store(StorageArea.DOCUMENTS, fileExtension, inputStream);
        }

        try {
            Document document = new Document();
            document.setFamilyId(familyId);
            document.setUploadedBy(currentUser.getId());
            document.setDocumentName(normalizeDocumentName(documentName));
            document.setDocumentType(documentType);
            document.setFilePath(storageKey);
            document.setExpiryDate(expiryDate);
            document.setFolderId(folderId);
            document.setHidden(hidden);
            if (protectionPassword != null && !protectionPassword.isBlank()) {
                document.setPasswordProtected(true);
                document.setPasswordHash(passwordEncoder.encode(protectionPassword));
            }

            Document savedDocument = documentRepository.saveAndFlush(document);
            DocumentResponse response = new DocumentResponse(savedDocument);

            String uploaderName = ((currentUser.getFirstName() != null ? currentUser.getFirstName() : "") + " " +
                                   (currentUser.getLastName() != null ? currentUser.getLastName() : "")).trim();
            if (uploaderName.isEmpty()) {
                uploaderName = currentUser.getEmail();
            }
            response.setUploadedByName(uploaderName);

            String notifMessage = uploaderName + " added a new document: " + savedDocument.getDocumentName();
            if (!savedDocument.isHidden() && (targetFolder == null || !targetFolder.isHidden())) {
                notificationService.notifyFamilyMembers(
                    familyId,
                    currentUser.getId(),
                    NotificationType.DOCUMENT_ADDED,
                    "New document added",
                    notifMessage,
                    currentUser.getId(),
                    "DOCUMENT",
                    savedDocument.getId(),
                    "/family/" + familyId + "?tab=documents",
                    "DOCUMENT_ADDED:" + savedDocument.getId()
                );
            }

            return response;
        } catch (RuntimeException exception) {
            try {
                fileStorageService.delete(StorageArea.DOCUMENTS, storageKey);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    public List<DocumentResponse> getDocumentsByFamily(Long familyId) {
        return getDocumentsByFamily(familyId, false);
    }

    public List<DocumentResponse> getDocumentsByFamily(Long familyId, boolean includeHidden) {
        return getDocumentsByFamily(familyId, includeHidden, null);
    }

    public List<DocumentResponse> getDocumentsByFamily(Long familyId, boolean includeHidden, String vaultToken) {
        User currentUser = getCurrentUser();
        FamilyMember membership = authorizationService.require(
                familyId, currentUser.getId(), Capability.DOCUMENT_VIEW);
        if (includeHidden) {
            vaultAccessService.requireUnlocked(familyId, vaultToken);
        }
        Map<Long, DocumentFolder> foldersById = folderRepository.findByFamilyIdOrderByNameAsc(familyId).stream()
                .collect(Collectors.toMap(DocumentFolder::getId, Function.identity()));

        return documentRepository.findByFamilyId(familyId).stream()
                .filter(doc -> {
                    DocumentFolder folder = foldersById.get(doc.getFolderId());
                    boolean isHidden = doc.isHidden() || folder != null && folder.isHidden();
                    return !isHidden || includeHidden && canAccessHiddenDocument(doc, folder, membership, currentUser.getId());
                })
                .map(doc -> {
                    DocumentResponse res = new DocumentResponse(doc);
                    if (doc.getUploadedBy() != null) {
                        userRepository.findById(doc.getUploadedBy())
                                .ifPresent(u -> res.setUploadedByName(u.getFirstName() + " " + u.getLastName()));
                    }
                    return res;
                })
                .collect(Collectors.toList());
    }

    public Resource getDocumentFile(Long documentId) throws IOException {
        return getDocumentFile(documentId, null, null);
    }

    public Resource getDocumentFile(Long documentId, String password) throws IOException {
        return getDocumentFile(documentId, password, null);
    }

    public Resource getDocumentFile(Long documentId, String password, String vaultToken) throws IOException {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        User currentUser = getCurrentUser();
        FamilyMember membership = authorizationService.require(
                document.getFamilyId(), currentUser.getId(), Capability.DOCUMENT_VIEW);
        verifyHiddenDocumentAccess(document, membership, currentUser.getId());
        DocumentFolder folder = getFolder(document.getFolderId(), document.getFamilyId());
        if (document.isHidden() || folder != null && folder.isHidden()) {
            vaultAccessService.requireUnlocked(document.getFamilyId(), vaultToken);
        }

        if (document.isPasswordProtected()
                && (password == null || document.getPasswordHash() == null || !passwordEncoder.matches(password, document.getPasswordHash()))) {
            throw new IllegalArgumentException("This document is password protected");
        }

        return fileStorageService.load(StorageArea.DOCUMENTS, document.getFilePath());
    }

    public Document getDocumentEntity(Long documentId) {
        return getDocumentEntity(documentId, null);
    }

    public Document getDocumentEntity(Long documentId, String vaultToken) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        User currentUser = getCurrentUser();
        FamilyMember membership = authorizationService.require(
                document.getFamilyId(), currentUser.getId(), Capability.DOCUMENT_VIEW);
        verifyHiddenDocumentAccess(document, membership, currentUser.getId());
        DocumentFolder folder = getFolder(document.getFolderId(), document.getFamilyId());
        if (document.isHidden() || folder != null && folder.isHidden()) {
            vaultAccessService.requireUnlocked(document.getFamilyId(), vaultToken);
        }
        return document;
    }

    @Transactional
    public DocumentResponse updateProtection(Long documentId, DocumentProtectionRequest request) {
        return updateProtection(documentId, request, null);
    }

    @Transactional
    public DocumentResponse updateProtection(Long documentId, DocumentProtectionRequest request, String vaultToken) {
        if (request == null) {
            throw new IllegalArgumentException("Document protection request is required");
        }
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        User currentUser = getCurrentUser();
        FamilyMember membership = authorizationService.require(
                document.getFamilyId(), currentUser.getId(), Capability.DOCUMENT_VIEW);
        boolean canManage = authorizationService.has(
                document.getFamilyId(), currentUser.getId(), Capability.DOCUMENT_EDIT_ANY)
                || document.getUploadedBy() != null
                && document.getUploadedBy().equals(currentUser.getId())
                && authorizationService.has(
                    document.getFamilyId(), currentUser.getId(), Capability.DOCUMENT_EDIT_OWN);
        if (!canManage) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to change document protection");
        }

        verifyHiddenDocumentAccess(document, membership, currentUser.getId());
        if (request.isHidden() && !currentUser.getId().equals(document.getUploadedBy())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the uploader can move this document to a personal vault");
        }
        DocumentFolder currentFolder = getFolder(document.getFolderId(), document.getFamilyId());
        if (document.isHidden() || request.isHidden() || currentFolder != null && currentFolder.isHidden()) {
            vaultAccessService.requireUnlocked(document.getFamilyId(), vaultToken);
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getPassword().length() < 6) {
                throw new IllegalArgumentException("Document password must be at least 6 characters");
            }
            document.setPasswordProtected(true);
            document.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        } else if (request.getPassword() != null) {
            document.setPasswordProtected(false);
            document.setPasswordHash(null);
        }
        document.setHidden(request.isHidden());
        if (request.getFolderId() != null) {
            DocumentFolder folder = folderRepository.findByIdAndFamilyId(request.getFolderId(), document.getFamilyId())
                    .orElseThrow(() -> new IllegalArgumentException("Folder does not belong to this family"));
            verifyFolderAccess(folder, membership, currentUser.getId());
            document.setFolderId(request.getFolderId());
        }
        return new DocumentResponse(documentRepository.save(document));
    }

    @Transactional
    public DocumentResponse updateDocument(Long documentId, DocumentUpdateRequest request) {
        return updateDocument(documentId, request, null);
    }

    @Transactional
    public DocumentResponse updateDocument(Long documentId, DocumentUpdateRequest request, String vaultToken) {
        if (request == null) {
            throw new IllegalArgumentException("Document update request is required");
        }
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        User currentUser = getCurrentUser();
        FamilyMember membership = authorizationService.require(
                document.getFamilyId(), currentUser.getId(), Capability.DOCUMENT_VIEW);
        boolean canEdit = authorizationService.has(
                document.getFamilyId(), currentUser.getId(), Capability.DOCUMENT_EDIT_ANY)
                || document.getUploadedBy() != null
                && document.getUploadedBy().equals(currentUser.getId())
                && authorizationService.has(
                    document.getFamilyId(), currentUser.getId(), Capability.DOCUMENT_EDIT_OWN);
        if (!canEdit) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to edit this document");
        }

        verifyHiddenDocumentAccess(document, membership, currentUser.getId());
        if (request.isHidden() && !currentUser.getId().equals(document.getUploadedBy())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the uploader can move this document to a personal vault");
        }
        DocumentFolder currentFolder = getFolder(document.getFolderId(), document.getFamilyId());
        if (document.isHidden() || request.isHidden() || currentFolder != null && currentFolder.isHidden()) {
            vaultAccessService.requireUnlocked(document.getFamilyId(), vaultToken);
        }

        document.setDocumentName(normalizeDocumentName(request.getDocumentName()));
        document.setDocumentType(request.getDocumentType());
        document.setExpiryDate(request.getExpiryDate());
        document.setHidden(request.isHidden());

        if (request.getFolderId() != null) {
            DocumentFolder folder = folderRepository.findByIdAndFamilyId(request.getFolderId(), document.getFamilyId())
                    .orElseThrow(() -> new IllegalArgumentException("Folder does not belong to this family"));
            verifyFolderAccess(folder, membership, currentUser.getId());
        }
        document.setFolderId(request.getFolderId());

        if (request.getPassword() != null) {
            if (request.getPassword().isBlank()) {
                document.setPasswordProtected(false);
                document.setPasswordHash(null);
            } else {
                if (request.getPassword().length() < 6) {
                    throw new IllegalArgumentException("Document password must be at least 6 characters");
                }
                document.setPasswordProtected(true);
                document.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            }
        }

        return new DocumentResponse(documentRepository.save(document));
    }

    @Transactional
    public void deleteDocument(Long documentId) throws IOException {
        deleteDocument(documentId, null);
    }

    @Transactional
    public void deleteDocument(Long documentId, String vaultToken) throws IOException {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        User currentUser = getCurrentUser();
        FamilyMember membership = authorizationService.require(document.getFamilyId(), currentUser.getId(), Capability.DOCUMENT_VIEW);
        boolean canDelete = authorizationService.has(
                document.getFamilyId(), currentUser.getId(), Capability.DOCUMENT_DELETE_ANY)
                || document.getUploadedBy() != null
                && document.getUploadedBy().equals(currentUser.getId())
                && authorizationService.has(
                    document.getFamilyId(), currentUser.getId(), Capability.DOCUMENT_DELETE_OWN);

        if (!canDelete) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to delete this document");
        }

        verifyHiddenDocumentAccess(document, membership, currentUser.getId());
        DocumentFolder folder = getFolder(document.getFolderId(), document.getFamilyId());
        if (document.isHidden() || folder != null && folder.isHidden()) {
            vaultAccessService.requireUnlocked(document.getFamilyId(), vaultToken);
        }

        documentRepository.delete(document);
        // The DB is authoritative: a failed commit must never destroy the only copy of a document.
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() {
                        try { fileStorageService.delete(StorageArea.DOCUMENTS, document.getFilePath()); }
                        catch (IOException exception) {
                            org.slf4j.LoggerFactory.getLogger(DocumentService.class)
                                    .warn("Post-commit document cleanup failed; orphan cleanup is required");
                        }
                    }
                });
    }
}
