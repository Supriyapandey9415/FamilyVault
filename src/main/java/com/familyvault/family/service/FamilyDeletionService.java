package com.familyvault.family.service;

import com.familyvault.document.entity.Document;
import com.familyvault.document.entity.DocumentFolder;
import com.familyvault.document.repository.DocumentFolderRepository;
import com.familyvault.document.repository.DocumentRepository;
import com.familyvault.family.entity.Family;
import com.familyvault.family.entity.FamilyInvitation;
import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.repository.FamilyInvitationRepository;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.family.repository.FamilyRepository;
import com.familyvault.memory.entity.Memory;
import com.familyvault.memory.entity.MemoryMedia;
import com.familyvault.memory.repository.MemoryMediaRepository;
import com.familyvault.memory.repository.MemoryRepository;
import com.familyvault.notification.repository.NotificationRepository;
import com.familyvault.settings.entity.FamilySettingsAudit;
import com.familyvault.settings.repository.FamilyNotificationPreferenceRepository;
import com.familyvault.settings.repository.FamilySettingsAuditRepository;
import com.familyvault.settings.repository.FamilySettingsRepository;
import com.familyvault.settings.security.Capability;
import com.familyvault.settings.security.FamilyAuthorizationService;
import com.familyvault.storage.FileStorageService;
import com.familyvault.storage.StorageArea;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FamilyDeletionService {

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyInvitationRepository invitationRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFolderRepository folderRepository;
    private final MemoryRepository memoryRepository;
    private final MemoryMediaRepository memoryMediaRepository;
    private final NotificationRepository notificationRepository;
    private final FamilySettingsRepository settingsRepository;
    private final FamilyNotificationPreferenceRepository familyPreferenceRepository;
    private final FamilySettingsAuditRepository auditRepository;
    private final FamilyAuthorizationService authorizationService;
    private final FileStorageService fileStorageService;

    public FamilyDeletionService(
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            FamilyInvitationRepository invitationRepository,
            DocumentRepository documentRepository,
            DocumentFolderRepository folderRepository,
            MemoryRepository memoryRepository,
            MemoryMediaRepository memoryMediaRepository,
            NotificationRepository notificationRepository,
            FamilySettingsRepository settingsRepository,
            FamilyNotificationPreferenceRepository familyPreferenceRepository,
            FamilySettingsAuditRepository auditRepository,
            FamilyAuthorizationService authorizationService,
            FileStorageService fileStorageService) {
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.invitationRepository = invitationRepository;
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.memoryRepository = memoryRepository;
        this.memoryMediaRepository = memoryMediaRepository;
        this.notificationRepository = notificationRepository;
        this.settingsRepository = settingsRepository;
        this.familyPreferenceRepository = familyPreferenceRepository;
        this.auditRepository = auditRepository;
        this.authorizationService = authorizationService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public void deleteFamily(Long familyId, Long actorUserId) {
        authorizationService.require(familyId, actorUserId, Capability.FAMILY_DELETE);
        Family family = familyRepository.findById(familyId)
            .orElseThrow(() -> new IllegalArgumentException("Family not found"));

        List<Document> documents = documentRepository.findByFamilyId(familyId);
        List<Memory> memories = memoryRepository.findByFamilyId(familyId);
        List<MemoryMedia> memoryMedia = new ArrayList<>();
        memories.forEach(memory -> memoryMedia.addAll(memoryMediaRepository.findByMemoryId(memory.getId())));
        List<DocumentFolder> folders = folderRepository.findByFamilyIdOrderByNameAsc(familyId);
        List<FamilyInvitation> invitations = invitationRepository.findByFamily_Id(familyId);
        List<FamilyMember> members = familyMemberRepository.findByFamilyId(familyId);

        recordDeletionAudit(family, actorUserId);
        memoryMediaRepository.deleteAll(memoryMedia);
        memoryRepository.deleteAll(memories);
        documentRepository.deleteAll(documents);
        folderRepository.deleteAll(folders);
        invitationRepository.deleteAll(invitations);
        notificationRepository.deleteByFamilyId(familyId);
        familyPreferenceRepository.deleteByFamilyId(familyId);
        settingsRepository.deleteById(familyId);
        familyMemberRepository.deleteAll(members);
        familyRepository.delete(family);

        afterCommit(() -> {
            documents.forEach(document -> deleteQuietly(StorageArea.DOCUMENTS, document.getFilePath()));
            memoryMedia.forEach(media -> deleteQuietly(StorageArea.MEMORIES, media.getFilePath()));
        });
    }

    private void recordDeletionAudit(Family family, Long actorUserId) {
        FamilySettingsAudit audit = new FamilySettingsAudit();
        audit.setFamilyId(family.getId());
        audit.setActorUserId(actorUserId);
        audit.setAction("FAMILY_DELETED");
        audit.setChangedFields("{\"deleted\":true}");
        audit.setRequestId(UUID.randomUUID().toString());
        auditRepository.save(audit);
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void deleteQuietly(StorageArea area, String storageKey) {
        try {
            fileStorageService.delete(area, storageKey);
        } catch (IOException | RuntimeException ignored) {
            // The database deletion remains valid; orphan cleanup can be retried separately.
        }
    }
}
