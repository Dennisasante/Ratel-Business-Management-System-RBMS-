package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record AiChatRequest(
        // Null starts a brand-new conversation.
        UUID conversationId,

        @NotBlank(message = "Message can't be empty")
        String message
) {
}
