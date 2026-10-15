package com.familyvault.settings.exception;

public class SettingsConflictException extends RuntimeException {
    public SettingsConflictException(String message) {
        super(message);
    }
}
