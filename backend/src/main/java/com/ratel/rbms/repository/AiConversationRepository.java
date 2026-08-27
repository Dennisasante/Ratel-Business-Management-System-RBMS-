package com.ratel.rbms.repository;

import com.ratel.rbms.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    List<AiConversation> findAllByBusinessIdOrderByLastMessageAtDesc(UUID businessId);

    Optional<AiConversation> findByIdAndBusinessId(UUID id, UUID businessId);

    long countByBusinessId(UUID businessId);

    long countByBusinessIdAndStatus(UUID businessId, String status);

    // Conversation-identity resolution for an external channel (§7/§32) —
    // the same external conversation ID on the same binding always resolves
    // back to the same AI conversation. Backed by a unique index (see V50).
    Optional<AiConversation> findByChannelBindingIdAndExternalConversationId(UUID channelBindingId, String externalConversationId);
}
