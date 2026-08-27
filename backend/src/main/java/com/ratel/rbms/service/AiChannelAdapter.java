package com.ratel.rbms.service;

import com.ratel.rbms.entity.enums.AiChannel;

/**
 * Everything channel-specific lives behind this interface — identifying the
 * channel, validating an inbound payload's shape, normalizing it into an
 * {@link IncomingAiMessage}, and delivering an {@link OutgoingAiMessage}
 * back out. An implementation NEVER queries a customer, creates a booking,
 * queries services, calls the LLM, or executes an AI tool — those only ever
 * happen inside AiChatService/AiToolService, reached exclusively through
 * AiChannelRouter (see spec §11).
 *
 * A future WhatsAppAdapter/InstagramAdapter/FacebookAdapter/VoiceAdapter
 * each implements this same interface — there is deliberately no separate
 * AI implementation per channel (spec §2). Only {@link WebDemoChannelAdapter}
 * exists in this phase.
 */
public interface AiChannelAdapter {

    AiChannel channel();

    /** Whether this payload is even shaped like a message this adapter understands — never touches AI/business logic. */
    boolean validateInbound(Object rawPayload);

    /** Turns a validated raw payload into the one shape the AI core understands. */
    IncomingAiMessage normalizeInbound(Object rawPayload);

    /** Delivers a normalized AI response back out through this channel. */
    void sendOutbound(OutgoingAiMessage message);
}
