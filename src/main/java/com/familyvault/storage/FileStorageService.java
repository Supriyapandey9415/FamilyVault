package com.familyvault.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorageService {

    String store(StorageArea area, String extension, InputStream inputStream) throws IOException;

    Resource load(StorageArea area, String storageKey) throws IOException;

    void delete(StorageArea area, String storageKey) throws IOException;
}
