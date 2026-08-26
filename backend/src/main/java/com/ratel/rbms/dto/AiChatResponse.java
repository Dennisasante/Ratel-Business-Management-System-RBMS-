package com.ratel.rbms.dto;

import java.util.List;
import java.util.UUID;

public record AiChatResponse(
        UUID conversationId,
        String assistantMessage,
        String conversationStatus,
        List<AiToolCallSummary> toolCalls
) {
}
