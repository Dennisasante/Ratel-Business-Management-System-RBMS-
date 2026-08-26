package com.ratel.rbms.dto;

import com.ratel.rbms.entity.AiConversation;

import java.time.Instant;
import java.util.UUID;

public record AiConversationSummaryResponse(
        UUID id,
        UUID customerId,
        String customerName,
        String channel,
        String status,
        Instant startedAt,
        Instant lastMessageAt
) {
    public static AiConversationSummaryResponse from(AiConversation c, String customerName) {
        return new AiConversationSummaryResponse(
                c.getId(), c.getCustomerId(), customerName, c.getChannel(), c.getStatus(),
                c.getStartedAt(), c.getLastMessageAt()
        );
    }
}
