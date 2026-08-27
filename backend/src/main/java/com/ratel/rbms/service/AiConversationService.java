package com.ratel.rbms.service;

import com.ratel.rbms.dto.AiActionEntry;
import com.ratel.rbms.dto.AiConversationDetailResponse;
import com.ratel.rbms.dto.AiConversationSummaryResponse;
import com.ratel.rbms.dto.AiMessageResponse;
import com.ratel.rbms.entity.AiConversation;
import com.ratel.rbms.entity.AiMessage;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.AiActionRepository;
import com.ratel.rbms.repository.AiConversationRepository;
import com.ratel.rbms.repository.AiMessageRepository;
import com.ratel.rbms.security.RateLimiterService;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns AiConversation/AiMessage persistence. Deliberately dumb on the write
 * side (same philosophy as PaymentTransactionService/NotificationService) —
 * AiChatService decides what to say and when to escalate; this just records
 * it, tenant-scoped throughout.
 */
@Service
public class AiConversationService {

    private final AiConversationRepository aiConversationRepository;
    private final AiMessageRepository aiMessageRepository;
    private final AiActionRepository aiActionRepository;
    private final ModuleAccessService moduleAccessService;
    private final CustomerService customerService;
    private final RateLimiterService rateLimiterService;

    public AiConversationService(
            AiConversationRepository aiConversationRepository,
            AiMessageRepository aiMessageRepository,
            AiActionRepository aiActionRepository,
            ModuleAccessService moduleAccessService,
            CustomerService customerService,
            RateLimiterService rateLimiterService
    ) {
        this.aiConversationRepository = aiConversationRepository;
        this.aiMessageRepository = aiMessageRepository;
        this.aiActionRepository = aiActionRepository;
        this.moduleAccessService = moduleAccessService;
        this.customerService = customerService;
        this.rateLimiterService = rateLimiterService;
    }

    public List<AiConversationSummaryResponse> list() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");
        return aiConversationRepository.findAllByBusinessIdOrderByLastMessageAtDesc(businessId).stream()
                .map(c -> AiConversationSummaryResponse.from(c, customerNameOrNull(c.getCustomerId())))
                .toList();
    }

    public AiConversationDetailResponse getDetail(UUID id) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");
        AiConversation conversation = getOwned(id, businessId);
        List<AiMessageResponse> messages = aiMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(AiMessageResponse::from)
                .toList();
        List<AiActionEntry> actions = aiActionRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(AiActionEntry::from)
                .toList();
        return AiConversationDetailResponse.from(conversation, customerNameOrNull(conversation.getCustomerId()), messages, actions);
    }

    // ---- Used internally by AiChatService — same tenant boundary, just
    // returning entities rather than DTOs since the caller needs to keep
    // mutating them within the same chat turn. ----

    AiConversation getOwnedEntity(UUID id, UUID businessId) {
        return getOwned(id, businessId);
    }

    @Transactional
    AiConversation createConversation(UUID businessId, String channel) {
        return createConversation(businessId, channel, null, null, null);
    }

    // Phase 3A: the same creation path, additionally stamping channel
    // identity when this conversation started on an external channel.
    // channelBindingId/externalConversationId/externalUserId are all null
    // for WEB_DEMO, exactly as before this phase existed.
    //
    // Rate-limited here rather than per-channel — this is the single choke
    // point every channel's "new conversation" ultimately goes through
    // (WEB_DEMO via createConversation(businessId, channel), an external
    // channel via AiChannelRouter.routeExternal), so one limit covers the
    // "excessive conversation creation" abuse case (§20) for all of them at
    // once. Generous relative to a single legitimate customer (a real
    // conversation only ever creates ONE of these), tight enough to stop a
    // flood of brand-new conversations from the same business's traffic.
    @Transactional
    AiConversation createConversation(UUID businessId, String channel, UUID channelBindingId,
                                       String externalConversationId, String externalUserId) {
        rateLimiterService.checkAllowed("ai-new-conversation:" + businessId, 30, Duration.ofMinutes(15));
        rateLimiterService.recordAttempt("ai-new-conversation:" + businessId);

        AiConversation conversation = AiConversation.builder()
                .businessId(businessId)
                .channel(channel)
                .channelBindingId(channelBindingId)
                .externalConversationId(externalConversationId)
                .externalUserId(externalUserId)
                .status("ACTIVE")
                .startedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build();
        return aiConversationRepository.save(conversation);
    }

    // Phase 3A: resolves the AI conversation an external message belongs to
    // — the same external conversation id on the same binding always
    // resolves back to the same AI conversation (DB-enforced, see V50),
    // creating a new one only the first time this external conversation is
    // ever seen.
    @Transactional
    AiConversation resolveOrCreateExternalConversation(UUID businessId, UUID channelBindingId, String channel,
                                                        String externalConversationId, String externalUserId) {
        return aiConversationRepository.findByChannelBindingIdAndExternalConversationId(channelBindingId, externalConversationId)
                .orElseGet(() -> createConversation(businessId, channel, channelBindingId, externalConversationId, externalUserId));
    }

    @Transactional
    AiMessage appendMessage(UUID businessId, UUID conversationId, String role, String content) {
        return appendMessage(businessId, conversationId, role, content, null, null);
    }

    // Phase 3A: the same append, additionally stamping channelBindingId/
    // externalMessageId when this message came from (or is answering) an
    // external channel message — null for every WEB_DEMO message, exactly
    // as before. This pair is what AiMessageRepository's idempotency lookup
    // is keyed on.
    @Transactional
    AiMessage appendMessage(UUID businessId, UUID conversationId, String role, String content,
                            UUID channelBindingId, String externalMessageId) {
        AiMessage message = AiMessage.builder()
                .businessId(businessId)
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .channelBindingId(channelBindingId)
                .externalMessageId(externalMessageId)
                .build();
        message = aiMessageRepository.save(message);

        AiConversation conversation = aiConversationRepository.findById(conversationId).orElse(null);
        if (conversation != null) {
            conversation.setLastMessageAt(Instant.now());
            aiConversationRepository.save(conversation);
        }
        return message;
    }

    List<AiMessage> history(UUID conversationId) {
        return aiMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional
    void linkCustomer(AiConversation conversation, UUID customerId) {
        if (conversation.getCustomerId() == null) {
            conversation.setCustomerId(customerId);
            aiConversationRepository.save(conversation);
        }
    }

    @Transactional
    void escalate(AiConversation conversation) {
        conversation.setStatus("ESCALATED");
        aiConversationRepository.save(conversation);
    }

    private AiConversation getOwned(UUID id, UUID businessId) {
        return aiConversationRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation not found."));
    }

    private String customerNameOrNull(UUID customerId) {
        return customerId == null ? null : customerService.getNameOrNull(customerId, TenantContext.getBusinessId());
    }
}
