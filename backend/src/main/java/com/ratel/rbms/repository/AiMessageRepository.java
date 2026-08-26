package com.ratel.rbms.repository;

import com.ratel.rbms.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    // conversationId alone is enough to scope this correctly since a
    // conversation is already tenant-checked before this is ever called
    // (see AiConversationService.getOwned) — matches ServiceOrderItemRepository's
    // own findAllByServiceOrderId, which doesn't re-check businessId either.
    List<AiMessage> findAllByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    long countByBusinessId(UUID businessId);
}
