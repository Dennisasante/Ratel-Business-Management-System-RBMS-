package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AiChatRequest(
        // Null starts a brand-new conversation.
        UUID conversationId,

        @NotBlank(message = "Message can't be empty")
        // Server-side cap (§21 of the Phase 3A spec) — matches
        // AiChatService.MAX_MESSAGE_LENGTH, which enforces this same limit
        // again for any channel that doesn't go through @Valid (i.e. every
        // external channel), so the limit is never just a frontend/DTO
        // formality.
        @Size(max = 4000, message = "Message is too long (max 4000 characters).")
        String message
) {
}
