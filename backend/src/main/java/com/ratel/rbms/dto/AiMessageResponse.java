package com.ratel.rbms.dto;

import com.ratel.rbms.entity.AiMessage;

import java.time.Instant;
import java.util.UUID;

public record AiMessageResponse(
        UUID id,
        String role,
        String content,
        Instant createdAt
) {
    public static AiMessageResponse from(AiMessage m) {
        return new AiMessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt());
    }
}
