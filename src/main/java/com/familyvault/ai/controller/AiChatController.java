package com.familyvault.ai.controller;

import com.familyvault.ai.dto.ChatMessageRequest;
import com.familyvault.ai.dto.ChatMessageResponse;
import com.familyvault.ai.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/ask")
    public ResponseEntity<ChatMessageResponse> ask(@Valid @RequestBody ChatMessageRequest request) {
        return ResponseEntity.ok(aiChatService.ask(request));
    }
}
