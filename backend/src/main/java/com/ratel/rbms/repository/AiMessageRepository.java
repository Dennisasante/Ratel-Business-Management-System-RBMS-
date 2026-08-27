package com.ratel.rbms.repository;

import com.ratel.rbms.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    // conversationId alone is enough to scope this correctly since a
    // conversation is already tenant-checked before this is ever called
    // (see AiConversationService.getOwned) — matches ServiceOrderItemRepository's
    // own findAllByServiceOrderId, which doesn't re-check businessId either.
    List<AiMessage> findAllByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    long countByBusinessId(UUID businessId);

    // The mandatory external-message idempotency check (§14/§33) — a
    // channel_binding_id + external_message_id pair that already exists
    // here means this exact external message was already processed; the
    // caller must short-circuit rather than reprocess it. Backed by a
    // unique index (see V50), so this is a real guarantee, not a
    // best-effort check.
    Optional<AiMessage> findByChannelBindingIdAndExternalMessageId(UUID channelBindingId, String externalMessageId);
}
