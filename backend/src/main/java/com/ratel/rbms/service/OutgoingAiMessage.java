package com.ratel.rbms.service;

import com.ratel.rbms.entity.enums.AiChannel;

import java.util.Map;
import java.util.UUID;

/**
 * The one shape the AI core ever hands back to a channel for delivery.
 * Text only for now — buttons/images/documents/voice are future work (see
 * spec §10). {@code replyToExternalMessageId} lets a channel that supports
 * threaded replies (WhatsApp, Instagram DMs) reply in-context; null when
 * the channel has no such concept (WEB_DEMO) or the adapter doesn't need it.
 */
public record OutgoingAiMessage(
        UUID conversationId,
        AiChannel channel,
        String text,
        String replyToExternalMessageId,
        Map<String, String> metadata
) {
}
