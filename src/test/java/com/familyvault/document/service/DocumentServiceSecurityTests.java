package com.familyvault.document.service;

import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.document.entity.Document;
import com.familyvault.document.repository.DocumentFolderRepository;
import com.familyvault.document.repository.DocumentRepository;
import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.entity.Role;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.notification.service.NotificationService;
import com.familyvault.settings.security.Capability;
import com.familyvault.settings.security.FamilyAuthorizationService;
import com.familyvault.storage.FileStorageService;
import com.familyvault.vault.service.VaultAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceSecurityTests {

    @Mock private DocumentRepository documentRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private DocumentFolderRepository folderRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private FileStorageService fileStorageService;
    @Mock private VaultAccessService vaultAccessService;
    @Mock private FamilyAuthorizationService authorizationService;

    private DocumentService documentService;
    private User currentUser;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(
                documentRepository,
                familyMemberRepository,
                userRepository,
                notificationService,
                folderRepository,
                passwordEncoder,
                fileStorageService,
                vaultAccessService,
                authorizationService
        );

        currentUser = new User();
        currentUser.setId(10L);
        currentUser.setEmail("member@example.com");
        currentUser.setFirstName("Family");
        currentUser.setLastName("Member");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser.getEmail(), null, List.of())
        );
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksAnotherMemberFromOpeningHiddenDocument() {
        FamilyMember membership = membership(Role.MEMBER);
        Document hiddenDocument = document(99L, true);

        when(documentRepository.findById(1L)).thenReturn(Optional.of(hiddenDocument));
        when(authorizationService.require(7L, currentUser.getId(), Capability.DOCUMENT_VIEW))
                .thenReturn(membership);

        assertThrows(AccessDeniedException.class, () -> documentService.getDocumentFile(1L));
    }

    @Test
    void blocksViewerFromUploadingDocument() {
        when(authorizationService.require(7L, currentUser.getId(), Capability.DOCUMENT_UPLOAD))
                .thenThrow(new AccessDeniedException("Your family role does not allow this action"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "%PDF-1.7\n".getBytes()
        );

        assertThrows(AccessDeniedException.class, () -> documentService.uploadDocument(
                7L, "Report", "LEGAL", null, file, null, false, null
        ));
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void rejectsUnsupportedDocumentExtension() {
        when(authorizationService.require(7L, currentUser.getId(), Capability.DOCUMENT_UPLOAD))
                .thenReturn(membership(Role.MEMBER));
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.html", "text/html", "<script></script>".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> documentService.uploadDocument(
                7L, "Unsafe file", "OTHER", null, file, null, false, null
        ));
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void removesStoredFileWhenDatabaseSaveFails() throws Exception {
        when(authorizationService.require(7L, currentUser.getId(), Capability.DOCUMENT_UPLOAD))
                .thenReturn(membership(Role.MEMBER));
        when(fileStorageService.store(eq(com.familyvault.storage.StorageArea.DOCUMENTS), eq("pdf"), any(InputStream.class)))
                .thenReturn("stored-file.pdf");
        when(documentRepository.saveAndFlush(any(Document.class)))
                .thenThrow(new IllegalStateException("Database unavailable"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "%PDF-1.7\n".getBytes()
        );

        assertThrows(IllegalStateException.class, () -> documentService.uploadDocument(
                7L, "Report", "LEGAL", null, file, null, false, null
        ));

        verify(fileStorageService).delete(
                com.familyvault.storage.StorageArea.DOCUMENTS,
                "stored-file.pdf"
        );
    }

    private FamilyMember membership(Role role) {
        FamilyMember membership = new FamilyMember();
        membership.setUserId(currentUser.getId());
        membership.setRole(role);
        return membership;
    }

    @Test
    void administratorCannotReadAnotherUsersPersonalDocument() throws Exception {
        Document hiddenDocument = document(99L, true);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(hiddenDocument));
        when(authorizationService.require(7L, currentUser.getId(), Capability.DOCUMENT_VIEW))
                .thenReturn(membership(Role.ADMIN));
        assertThrows(AccessDeniedException.class, () -> documentService.getDocumentFile(1L, null, "vault-token"));
        verify(fileStorageService, never()).load(any(), any());
    }

    @Test
    void privateUploadNeverBroadcastsItsTitle() throws Exception {
        when(authorizationService.require(7L, currentUser.getId(), Capability.DOCUMENT_UPLOAD))
                .thenReturn(membership(Role.MEMBER));
        when(fileStorageService.store(eq(com.familyvault.storage.StorageArea.DOCUMENTS), eq("pdf"), any(InputStream.class)))
                .thenReturn("stored-file.pdf");
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> {
            Document saved = invocation.getArgument(0); saved.setId(5L); return saved;
        });
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "%PDF-1.7\n".getBytes());
        documentService.uploadDocument(7L, "Private medical diagnosis", "MEDICAL", null, file, null, true, null, "vault-token");
        org.mockito.Mockito.verifyNoInteractions(notificationService);
    }

    @Test
    void executableRenamedAsPdfIsRejected() {
        when(authorizationService.require(7L, currentUser.getId(), Capability.DOCUMENT_UPLOAD))
                .thenReturn(membership(Role.MEMBER));
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "MZfake executable".getBytes());
        assertThrows(IllegalArgumentException.class, () -> documentService.uploadDocument(7L, "Report", "OTHER", null, file));
        org.mockito.Mockito.verifyNoInteractions(fileStorageService);
    }

    private Document document(Long uploadedBy, boolean hidden) {
        Document document = new Document();
        document.setId(1L);
        document.setFamilyId(7L);
        document.setUploadedBy(uploadedBy);
        document.setDocumentName("Private report");
        document.setDocumentType("LEGAL");
        document.setFilePath("uploads/documents/private.pdf");
        document.setHidden(hidden);
        return document;
    }
}
