package com.familyvault.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final Path storageRoot;

    public LocalFileStorageService(@Value("${app.storage.root:uploads}") String storageRoot) {
        if (storageRoot == null || storageRoot.isBlank()) {
            throw new IllegalArgumentException("Storage root is required");
        }
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() throws IOException {
        Files.createDirectories(storageRoot);
        for (StorageArea area : StorageArea.values()) {
            Files.createDirectories(areaRoot(area));
        }
    }

    @Override
    public String store(StorageArea area, String extension, InputStream inputStream) throws IOException {
        if (area == null) {
            throw new IllegalArgumentException("Storage area is required");
        }
        if (inputStream == null) {
            throw new IllegalArgumentException("File content is required");
        }

        String safeExtension = normalizeExtension(extension);
        String storageKey = UUID.randomUUID() + "." + safeExtension;
        Path target = resolve(area, storageKey);
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target);
        return storageKey;
    }

    @Override
    public Resource load(StorageArea area, String storageKey) throws IOException {
        Path file = resolve(area, storageKey);
        Resource resource = new UrlResource(file.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException("Stored file is not available");
        }
        return resource;
    }

    @Override
    public void delete(StorageArea area, String storageKey) throws IOException {
        Files.deleteIfExists(resolve(area, storageKey));
    }

    private String normalizeExtension(String extension) {
        String normalized = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9]{1,10}")) {
            throw new IllegalArgumentException("Invalid file extension");
        }
        return normalized;
    }

    private Path areaRoot(StorageArea area) {
        return storageRoot.resolve(area.getDirectory()).normalize();
    }

    private Path resolve(StorageArea area, String storageKey) {
        if (area == null) {
            throw new IllegalArgumentException("Storage area is required");
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key is required");
        }

        Path root = areaRoot(area);
        Path suppliedPath = Paths.get(storageKey);
        Path legacyPath = suppliedPath.toAbsolutePath().normalize();
        Path resolved = legacyPath.startsWith(root)
                ? legacyPath
                : root.resolve(suppliedPath).normalize();

        if (!resolved.startsWith(root)) {
            throw new SecurityException("Storage key points outside the configured storage area");
        }
        return resolved;
    }
}
