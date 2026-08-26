package com.ratel.rbms.dto;

public record AiOverviewResponse(
        boolean active,
        String agentName,
        long conversationCount,
        long escalatedCount,
        long actionCount,
        long knowledgeEntryCount
) {
}
