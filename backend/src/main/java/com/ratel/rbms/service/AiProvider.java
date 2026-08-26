package com.ratel.rbms.service;

import java.util.List;

/**
 * Abstraction over "whichever LLM actually answers." Phase 1 has exactly
 * one implementation (OpenAiProvider) — this interface exists so a future
 * provider swap/addition (or a stub for tests) never touches AiChatService,
 * same reasoning as WhatsAppLinkService/EmailService sitting behind their
 * own small interfaces-in-spirit even though this is the only real one today.
 */
public interface AiProvider {

    /** False when no API key is configured — the caller shows a clear "not set up yet" message rather than attempting a call that will fail. */
    boolean isConfigured();

    AiProviderResult chat(String systemPrompt, List<AiProviderMessage> conversation, List<AiToolDefinition> tools);
}
