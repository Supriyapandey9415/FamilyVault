package com.familyvault.ai.dto;

public class ChatMessageResponse {

    private final String reply;

    public ChatMessageResponse(String reply) {
        this.reply = reply;
    }

    public String getReply() {
        return reply;
    }
}
