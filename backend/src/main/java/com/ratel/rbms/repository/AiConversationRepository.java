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
}
