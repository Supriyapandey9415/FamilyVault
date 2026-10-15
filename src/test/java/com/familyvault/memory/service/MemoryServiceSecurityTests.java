package com.familyvault.memory.service;

import com.familyvault.auth.entity.User;
import com.familyvault.common.security.CurrentUserService;
import com.familyvault.memory.dto.MemoryRequest;
import com.familyvault.memory.repository.MemoryMediaRepository;
import com.familyvault.memory.repository.MemoryRepository;
import com.familyvault.notification.service.NotificationService;
import com.familyvault.settings.security.Capability;
import com.familyvault.settings.security.FamilyAuthorizationService;
import com.familyvault.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryServiceSecurityTests {

    @Mock private MemoryRepository memoryRepository;
    @Mock private MemoryMediaRepository mediaRepository;
    @Mock private NotificationService notificationService;
    @Mock private CurrentUserService currentUserService;
    @Mock private FamilyAuthorizationService authorizationService;
    @Mock private FileStorageService fileStorageService;

    private MemoryService service;

    @BeforeEach
    void setUp() {
        service = new MemoryService(
            memoryRepository,
            mediaRepository,
            notificationService,
            currentUserService,
            authorizationService,
            fileStorageService
        );
    }

    @Test
    void viewerCannotCreateMemory() {
        User user = new User();
        user.setId(12L);
        MemoryRequest request = new MemoryRequest();
        request.setFamilyId(7L);
        request.setTitle("Family trip");
        when(currentUserService.requireUser()).thenReturn(user);
        when(authorizationService.require(7L, 12L, Capability.MEMORY_CREATE))
            .thenThrow(new AccessDeniedException("Your family role does not allow this action"));

        assertThrows(AccessDeniedException.class, () -> service.createMemory(request));
        verify(memoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
