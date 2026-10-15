package com.familyvault.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageServiceTests {

    @TempDir
    Path temporaryDirectory;

    private LocalFileStorageService storageService;

    @BeforeEach
    void setUp() throws Exception {
        storageService = new LocalFileStorageService(temporaryDirectory.toString());
        storageService.initialize();
    }

    @Test
    void storesLoadsAndDeletesUsingOpaqueStorageKey() throws Exception {
        byte[] content = "family document".getBytes();

        String storageKey = storageService.store(
                StorageArea.DOCUMENTS,
                "pdf",
                new ByteArrayInputStream(content)
        );

        assertTrue(storageKey.matches("[0-9a-f-]{36}\\.pdf"));
        Resource resource = storageService.load(StorageArea.DOCUMENTS, storageKey);
        try (InputStream inputStream = resource.getInputStream()) {
            assertArrayEquals(content, inputStream.readAllBytes());
        }

        storageService.delete(StorageArea.DOCUMENTS, storageKey);
        assertFalse(resource.exists());
    }

    @Test
    void rejectsStorageKeysOutsideConfiguredArea() {
        assertThrows(SecurityException.class,
                () -> storageService.load(StorageArea.DOCUMENTS, "../outside.pdf"));
    }

    @Test
    void supportsLegacyDocumentPathsInsideConfiguredArea() throws Exception {
        Path legacyFile = temporaryDirectory.resolve("documents").resolve("legacy.pdf");
        Files.writeString(legacyFile, "legacy");

        Resource resource = storageService.load(StorageArea.DOCUMENTS, legacyFile.toString());

        assertTrue(resource.exists());
    }
}
