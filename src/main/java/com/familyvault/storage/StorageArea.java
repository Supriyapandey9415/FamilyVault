package com.familyvault.storage;

public enum StorageArea {
    DOCUMENTS("documents"),
    MEMORIES("memories");

    private final String directory;

    StorageArea(String directory) {
        this.directory = directory;
    }

    public String getDirectory() {
        return directory;
    }
}
