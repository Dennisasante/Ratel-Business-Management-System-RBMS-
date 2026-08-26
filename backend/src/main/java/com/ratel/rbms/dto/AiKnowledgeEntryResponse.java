package com.ratel.rbms.dto;

import com.ratel.rbms.entity.AiKnowledgeEntry;

import java.time.Instant;
import java.util.UUID;

public record AiKnowledgeEntryResponse(
        UUID id,
        String title,
        String content,
        String category,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static AiKnowledgeEntryResponse from(AiKnowledgeEntry e) {
        return new AiKnowledgeEntryResponse(
                e.getId(), e.getTitle(), e.getContent(), e.getCategory(),
                e.isActive(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
