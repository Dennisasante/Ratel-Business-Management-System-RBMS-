package com.ratel.rbms.service;

import com.ratel.rbms.dto.AiChatResponse;
import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.AiConversation;
import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.AiChannelBindingRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Owns identity and business resolution for every channel — the ONLY place
 * that ever decides "which business does this message belong to." Contains
 * zero AI logic (no system prompt, no LLM call, no tool execution — that's
 * AiChatService.processTurn) and zero external I/O (no webhook signature
 * verification, no external send — that's an AiChannelAdapter). Once a
 * business is resolved, everything downstream is already tenant-scoped
 * exactly like every other RBMS service (spec §13).
 */
@Service
public class AiChannelRouter {

    private final AiChannelBindingRepository aiChannelBindingRepository;
    private final AiConversationService aiConversationService;
    private final AiChatService aiChatService;
    private final ModuleAccessService moduleAccessService;

    public AiChannelRouter(
            AiChannelBindingRepository aiChannelBindingRepository,
            AiConversationService aiConversationService,
            AiChatService aiChatService,
            ModuleAccessService moduleAccessService
    ) {
        this.aiChannelBindingRepository = aiChannelBindingRepository;
        this.aiConversationService = aiConversationService;
        this.aiChatService = aiChatService;
        this.moduleAccessService = moduleAccessService;
    }

    /**
     * WEB_DEMO's own path — business comes ONLY from the authenticated
     * caller's TenantContext, exactly as AiChatService.chat() has always
     * resolved it. Never accepts a business id from anywhere else. This is
     * what AiChatController calls today; AiChatService.chat(AiChatRequest)
     * itself is kept fully intact for backward compatibility with existing
     * callers/tests, but the real HTTP entry point now goes through here.
     */
    @Transactional
    public AiChatResponse routeWebDemo(UUID conversationId, String messageText) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");

        AiConversation conversation = conversationId != null
                ? aiConversationService.getOwnedEntity(conversationId, businessId)
                : aiConversationService.createConversation(businessId, AiChannel.WEB_DEMO.name());

        return aiChatService.processTurn(businessId, conversation, messageText, null);
    }

    /**
     * The external-channel path. Not wired to any real channel yet — no
     * WhatsApp/Instagram/Facebook/Phone integration exists in this phase;
     * this exists so a future webhook controller has somewhere correct to
     * call (verify signature -> normalize via an AiChannelAdapter -> here).
     *
     * business_id is NEVER taken from the incoming message itself (spec
     * §13/§29) — it only ever comes from the AiChannelBinding that
     * externalAccountId resolves to. An externalAccountId that resolves to
     * no binding, or to an inactive one, is rejected outright rather than
     * guessed at — ambiguous/unrecognized routing is never silently
     * allowed through.
     */
    @Transactional
    public AiChatResponse routeExternal(IncomingAiMessage message, String externalAccountId) {
        AiChannelBinding binding = aiChannelBindingRepository
                .findByChannelAndExternalAccountId(message.channel(), externalAccountId)
                .filter(AiChannelBinding::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No active channel binding for this account."));

        UUID businessId = binding.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");

        AiConversation conversation = message.externalConversationId() != null
                ? aiConversationService.resolveOrCreateExternalConversation(
                        businessId, binding.getId(), message.channel().name(),
                        message.externalConversationId(), message.externalUserId())
                : aiConversationService.createConversation(
                        businessId, message.channel().name(), binding.getId(), null, message.externalUserId());

        // Idempotency (§14/§33) is enforced inside processTurn itself,
        // keyed on this conversation's channelBindingId + externalMessageId
        // — a second delivery of the same external message short-circuits
        // there without this router needing its own duplicate check.
        return aiChatService.processTurn(businessId, conversation, message.text(), message.externalMessageId());
    }
}
