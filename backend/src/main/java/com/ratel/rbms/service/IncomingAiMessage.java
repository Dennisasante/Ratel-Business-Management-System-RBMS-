package com.ratel.rbms.service;

import com.ratel.rbms.entity.enums.AiChannel;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The one shape every channel normalizes an inbound message into before the
 * AI core ever sees it. AiChatService/AiChannelRouter never receive a
 * WhatsApp/Instagram/Facebook-specific request type — only this. Channel
 * adapters build these (see AiChannelAdapter.normalizeInbound); nothing
 * downstream needs to know which channel it came from beyond reading the
 * {@code channel} field itself.
 *
 * channelBindingId is deliberately NOT trusted as "the business" — it's
 * only ever used by AiChannelRouter to look up the real AiChannelBinding
 * row (and from there, the actual business_id) — see spec §13/§29.
 * externalMessageId is what makes this message idempotent (§14) — null for
 * WEB_DEMO, which has no external delivery retries to guard against.
 */
public record IncomingAiMessage(
        AiChannel channel,
        UUID channelBindingId,
        String externalConversationId,
        String externalMessageId,
        String externalUserId,
        String text,
        Instant timestamp,
        Map<String, String> metadata
) {
}
