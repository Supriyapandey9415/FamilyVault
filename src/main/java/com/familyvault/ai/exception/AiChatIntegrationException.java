package com.familyvault.ai.exception;

public class AiChatIntegrationException extends RuntimeException {

    public AiChatIntegrationException(String message) {
        super(message);
    }

    public AiChatIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
