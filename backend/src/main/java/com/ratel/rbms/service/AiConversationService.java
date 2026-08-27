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
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public AiConversationService(
            AiConversationRepository aiConversationRepository,
            AiMessageRepository aiMessageRepository,
            AiActionRepository aiActionRepository,
            ModuleAccessService moduleAccessService,
            CustomerService customerService
    ) {
        this.aiConversationRepository = aiConversationRepository;
        this.aiMessageRepository = aiMessageRepository;
        this.aiActionRepository = aiActionRepository;
        this.moduleAccessService = moduleAccessService;
        this.customerService = customerService;
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
        AiConversation conversation = AiConversation.builder()
                .businessId(businessId)
                .channel(channel)
                .status("ACTIVE")
                .startedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build();
        return aiConversationRepository.save(conversation);
    }

    @Transactional
    AiMessage appendMessage(UUID businessId, UUID conversationId, String role, String content) {
        AiMessage message = AiMessage.builder()
                .businessId(businessId)
                .conversationId(conversationId)
                .role(role)
                .content(content)
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
