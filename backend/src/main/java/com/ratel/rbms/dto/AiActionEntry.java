package com.ratel.rbms.dto;

import com.ratel.rbms.entity.AiAction;

import java.time.Instant;

// Internal/developer-facing timeline entry for a conversation's detail view
// — deliberately narrow (tool name, status, when) so it can sit alongside
// the message bubbles without ever exposing raw arguments/results, which
// could echo back whatever the model or a demo customer supplied.
public record AiActionEntry(
        String toolName,
        String status,
        Instant createdAt
) {
    public static AiActionEntry from(AiAction action) {
        return new AiActionEntry(action.getToolName(), action.getStatus(), action.getCreatedAt());
    }
}
