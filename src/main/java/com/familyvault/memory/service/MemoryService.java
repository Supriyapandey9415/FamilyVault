package com.familyvault.memory.service;

import com.familyvault.auth.entity.User;
import com.familyvault.common.security.CurrentUserService;
import com.familyvault.memory.dto.MemoryMediaResponse;
import com.familyvault.memory.dto.MemoryRequest;
import com.familyvault.memory.dto.MemoryResponse;
import com.familyvault.memory.entity.Memory;
import com.familyvault.memory.entity.MemoryMedia;
import com.familyvault.memory.repository.MemoryMediaRepository;
import com.familyvault.memory.repository.MemoryRepository;
import com.familyvault.notification.entity.NotificationType;
import com.familyvault.notification.service.NotificationService;
import com.familyvault.settings.security.Capability;
import com.familyvault.settings.security.FamilyAuthorizationService;
import com.familyvault.storage.FileStorageService;
import com.familyvault.storage.StorageArea;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MemoryService {

    private static final long MAX_MEDIA_SIZE = 15L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp"
    );

    private final MemoryRepository memoryRepository;
    private final MemoryMediaRepository memoryMediaRepository;
    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;
    private final FamilyAuthorizationService authorizationService;
    private final FileStorageService fileStorageService;

    public MemoryService(
            MemoryRepository memoryRepository,
            MemoryMediaRepository memoryMediaRepository,
            NotificationService notificationService,
            CurrentUserService currentUserService,
            FamilyAuthorizationService authorizationService,
            FileStorageService fileStorageService) {
        this.memoryRepository = memoryRepository;
        this.memoryMediaRepository = memoryMediaRepository;
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public MemoryResponse createMemory(MemoryRequest request) {
        User currentUser = currentUserService.requireUser();
        authorizationService.require(request.getFamilyId(), currentUser.getId(), Capability.MEMORY_CREATE);

        Memory memory = new Memory();
        memory.setFamilyId(request.getFamilyId());
        memory.setTitle(request.getTitle().trim());
        memory.setDescription(request.getDescription());
        memory.setMemoryDate(request.getMemoryDate());
        memory.setLocation(request.getLocation());
        memory.setCreatedBy(currentUser.getId());
        Memory savedMemory = memoryRepository.save(memory);

        String uploaderName = displayName(currentUser);
        notificationService.notifyFamilyMembers(
            request.getFamilyId(),
            currentUser.getId(),
            NotificationType.MEMORY_ADDED,
            "New memory added",
            uploaderName + " added a new memory: " + savedMemory.getTitle(),
            currentUser.getId(),
            "MEMORY",
            savedMemory.getId(),
            "/family/" + request.getFamilyId() + "?tab=memories",
            "MEMORY_ADDED:" + savedMemory.getId()
        );
        return toResponse(savedMemory);
    }

    @Transactional(readOnly = true)
    public List<MemoryResponse> getMemoriesByFamily(Long familyId) {
        User currentUser = currentUserService.requireUser();
        authorizationService.require(familyId, currentUser.getId(), Capability.MEMORY_VIEW);
        return memoryRepository.findByFamilyId(familyId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MemoryResponse getMemoryById(Long id) {
        Memory memory = requireMemory(id);
        User currentUser = currentUserService.requireUser();
        authorizationService.require(memory.getFamilyId(), currentUser.getId(), Capability.MEMORY_VIEW);
        return toResponse(memory);
    }

    @Transactional
    public MemoryMediaResponse uploadMedia(Long memoryId, MultipartFile file) throws IOException {
        Memory memory = requireMemory(memoryId);
        User currentUser = currentUserService.requireUser();
        requireCanEdit(memory, currentUser.getId());
        String extension = validateMedia(file);

        String newStorageKey;
        try (var inputStream = file.getInputStream()) {
            newStorageKey = fileStorageService.store(StorageArea.MEMORIES, extension, inputStream);
        }

        List<MemoryMedia> previousMedia = memoryMediaRepository.findByMemoryId(memoryId);
        registerMediaReplacementCleanup(newStorageKey, previousMedia);
        try {
            MemoryMedia memoryMedia = new MemoryMedia();
            memoryMedia.setMemory(memory);
            memoryMedia.setFileName(safeOriginalName(file.getOriginalFilename()));
            memoryMedia.setFilePath(newStorageKey);
            memoryMedia.setFileType(file.getContentType());
            MemoryMedia savedMedia = memoryMediaRepository.saveAndFlush(memoryMedia);
            memoryMediaRepository.deleteAll(previousMedia);
            return new MemoryMediaResponse(savedMedia);
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                deleteQuietly(newStorageKey);
            }
            throw exception;
        }
    }

    @Transactional
    public MemoryResponse updateMemory(Long id, MemoryRequest request) {
        Memory memory = requireMemory(id);
        User currentUser = currentUserService.requireUser();
        requireCanEdit(memory, currentUser.getId());

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            memory.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            memory.setDescription(request.getDescription());
        }
        if (request.getMemoryDate() != null) {
            memory.setMemoryDate(request.getMemoryDate());
        }
        if (request.getLocation() != null) {
            memory.setLocation(request.getLocation());
        }
        return toResponse(memoryRepository.save(memory));
    }

    @Transactional
    public void deleteMemory(Long id) {
        Memory memory = requireMemory(id);
        User currentUser = currentUserService.requireUser();
        requireCanDelete(memory, currentUser.getId());

        List<MemoryMedia> media = memoryMediaRepository.findByMemoryId(id);
        memoryMediaRepository.deleteAll(media);
        memoryRepository.delete(memory);
        afterCommit(() -> media.forEach(item -> deleteQuietly(item.getFilePath())));
    }

    @Transactional(readOnly = true)
    public Resource getMediaFile(Long mediaId) throws IOException {
        MemoryMedia media = getMediaEntity(mediaId);
        return fileStorageService.load(StorageArea.MEMORIES, media.getFilePath());
    }

    @Transactional(readOnly = true)
    public MemoryMedia getMediaEntity(Long mediaId) {
        MemoryMedia media = memoryMediaRepository.findById(mediaId)
            .orElseThrow(() -> new IllegalArgumentException("Media not found"));
        User currentUser = currentUserService.requireUser();
        authorizationService.require(
            media.getMemory().getFamilyId(), currentUser.getId(), Capability.MEMORY_VIEW);
        return media;
    }

    private Memory requireMemory(Long id) {
        return memoryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
    }

    private void requireCanEdit(Memory memory, Long userId) {
        authorizationService.require(memory.getFamilyId(), userId, Capability.MEMORY_VIEW);
        boolean allowed = authorizationService.has(memory.getFamilyId(), userId, Capability.MEMORY_EDIT_ANY)
            || userId.equals(memory.getCreatedBy())
            && authorizationService.has(memory.getFamilyId(), userId, Capability.MEMORY_EDIT_OWN);
        if (!allowed) {
            throw new AccessDeniedException("You do not have permission to modify this memory");
        }
    }

    private void requireCanDelete(Memory memory, Long userId) {
        authorizationService.require(memory.getFamilyId(), userId, Capability.MEMORY_VIEW);
        boolean allowed = authorizationService.has(memory.getFamilyId(), userId, Capability.MEMORY_DELETE_ANY)
            || userId.equals(memory.getCreatedBy())
            && authorizationService.has(memory.getFamilyId(), userId, Capability.MEMORY_DELETE_OWN);
        if (!allowed) {
            throw new AccessDeniedException("You do not have permission to delete this memory");
        }
    }

    private MemoryResponse toResponse(Memory memory) {
        MemoryResponse response = new MemoryResponse(memory);
        memoryMediaRepository.findByMemoryId(memory.getId()).stream().findFirst()
            .ifPresent(media -> response.setFileUrl("/api/memories/media/" + media.getId()));
        return response;
    }

    private String validateMedia(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty image is required");
        }
        if (file.getSize() > MAX_MEDIA_SIZE) {
            throw new IllegalArgumentException("Memory image size must not exceed 15MB");
        }
        String originalName = safeOriginalName(file.getOriginalFilename());
        int extensionIndex = originalName.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex == originalName.length() - 1) {
            throw new IllegalArgumentException("Memory image type is not supported");
        }
        String extension = originalName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension) || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPG, PNG, and WebP memory images are supported");
        }
        com.familyvault.storage.UploadContentValidator.validate(file, extension);
        return extension;
    }

    private String safeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("Image filename is required");
        }
        String normalized = originalName.replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (baseName.isBlank() || baseName.length() > 255 || baseName.contains("\r") || baseName.contains("\n")) {
            throw new IllegalArgumentException("Image filename is invalid");
        }
        return baseName;
    }

    private void registerMediaReplacementCleanup(String newStorageKey, List<MemoryMedia> previousMedia) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    previousMedia.forEach(media -> deleteQuietly(media.getFilePath()));
                } else {
                    deleteQuietly(newStorageKey);
                }
            }
        });
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

    private void deleteQuietly(String storageKey) {
        try {
            fileStorageService.delete(StorageArea.MEMORIES, storageKey);
        } catch (IOException | RuntimeException ignored) {
            // Database state remains authoritative; orphan cleanup can retry asynchronously.
        }
    }

    private String displayName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
            + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isBlank() ? user.getEmail() : name;
    }
}
