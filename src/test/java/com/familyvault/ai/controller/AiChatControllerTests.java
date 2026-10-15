package com.familyvault.ai.controller;

import com.familyvault.ai.dto.ChatMessageResponse;
import com.familyvault.ai.service.AiChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiChatControllerTests {

    @Mock
    private AiChatService aiChatService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiChatController(aiChatService))
                .build();
    }

    @Test
    void acceptsMessageOnlyRequestAndReturnsReply() throws Exception {
        when(aiChatService.ask(any()))
                .thenReturn(new ChatMessageResponse("I am looking into your vault for that document."));

        mockMvc.perform(post("/api/chat/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Where is my document?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply")
                        .value("I am looking into your vault for that document."));
    }
}
