package com.ratel.rbms.dto;

// Developer/admin-facing view of one tool call made during a single chat
// turn — shown in the dashboard's Test AI panel only, clearly marked there
// as internal information. Deliberately excludes raw argumentsJson/resultJson
// (which could echo back whatever the model supplied) in favor of a short
// human-readable summary — see AiChatService for what actually gets put here.
public record AiToolCallSummary(
        String toolName,
        String status,
        String summary
) {
}
