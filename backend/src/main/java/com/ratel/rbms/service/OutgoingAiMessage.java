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
    // Well-known metadata keys AiChannelRouter populates for any non-WEB_DEMO
    // channel (spec §18 of Phase 3B: an adapter must never receive an
    // AiChannelBinding/repository/AiConversation directly — only these two
    // plain, non-sensitive routing strings). Every external channel adapter
    // resolves its own binding/credentials from CHANNEL_BINDING_ID via its
    // own repository lookup; nothing sensitive ever travels through this map.
    public static final String CHANNEL_BINDING_ID_KEY = "channelBindingId";
    public static final String RECIPIENT_EXTERNAL_USER_ID_KEY = "recipientExternalUserId";
}
