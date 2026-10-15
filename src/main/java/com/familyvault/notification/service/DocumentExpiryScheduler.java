package com.familyvault.notification.service;

import com.familyvault.document.entity.Document;
import com.familyvault.document.repository.DocumentRepository;
import com.familyvault.notification.entity.NotificationType;
import com.familyvault.settings.entity.FamilySettings;
import com.familyvault.settings.repository.FamilySettingsRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentExpiryScheduler {

    private final DocumentRepository documentRepository;
    private final NotificationService notificationService;
    private final FamilySettingsRepository familySettingsRepository;

    public DocumentExpiryScheduler(DocumentRepository documentRepository,
                                   NotificationService notificationService,
                                   FamilySettingsRepository familySettingsRepository) {
        this.documentRepository = documentRepository;
        this.notificationService = notificationService;
        this.familySettingsRepository = familySettingsRepository;
    }

    @Scheduled(
        cron = "${app.notifications.expiry-cron:0 0 8 * * *}",
        zone = "${app.notifications.scheduler-zone:UTC}"
    )
    public void checkDocumentExpiries() {
        LocalDate today = LocalDate.now();
        Map<Long, FamilySettings> settingsByFamily = familySettingsRepository.findAll().stream()
                .collect(Collectors.toMap(FamilySettings::getFamilyId, Function.identity()));
        int maximumReminderDays = settingsByFamily.values().stream()
                .mapToInt(FamilySettings::getDocumentExpiryReminderDays)
                .max()
                .orElse(30);
        List<Document> documents = documentRepository.findByExpiryDateBetweenOrderByExpiryDateAsc(
                today.plusDays(1), today.plusDays(maximumReminderDays));
        
        for (Document doc : documents) {
            int reminderDays = settingsByFamily.containsKey(doc.getFamilyId())
                    ? settingsByFamily.get(doc.getFamilyId()).getDocumentExpiryReminderDays()
                    : 30;
            if (!doc.getExpiryDate().equals(today.plusDays(reminderDays))) {
                continue;
            }
            // The notification tray is visible while the vault is locked; do not include document metadata.
            String message = "A document expires on " + doc.getExpiryDate() + ". Open Documents to review your accessible files.";
            notificationService.createNotification(
                    doc.getUploadedBy(),
                    doc.getFamilyId(),
                    NotificationType.DOCUMENT_EXPIRING,
                    "Document expires in " + reminderDays + " days",
                    message,
                    null,
                    "DOCUMENT",
                    doc.getId(),
                    "/family/" + doc.getFamilyId() + "?tab=documents",
                    "DOCUMENT_EXPIRING_" + reminderDays + ":" + doc.getId() + ":" + doc.getExpiryDate()
            );
        }
    }
}
