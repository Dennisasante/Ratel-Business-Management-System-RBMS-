package com.ratel.rbms.repository;

import com.ratel.rbms.entity.AiAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiActionRepository extends JpaRepository<AiAction, UUID> {

    List<AiAction> findAllByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    long countByBusinessId(UUID businessId);
}
