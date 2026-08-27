package com.ratel.rbms.dto;

import com.ratel.rbms.entity.AiConversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiConversationDetailResponse(
        UUID id,
        UUID customerId,
        String customerName,
        String channel,
        String status,
        Instant startedAt,
        Instant lastMessageAt,
        List<AiMessageResponse> messages,
        List<AiActionEntry> actions
) {
    public static AiConversationDetailResponse from(
            AiConversation c, String customerName, List<AiMessageResponse> messages, List<AiActionEntry> actions
    ) {
        return new AiConversationDetailResponse(
                c.getId(), c.getCustomerId(), customerName, c.getChannel(), c.getStatus(),
                c.getStartedAt(), c.getLastMessageAt(), messages, actions
        );
    }
}
