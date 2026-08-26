package com.ratel.rbms.repository;

import com.ratel.rbms.entity.AiKnowledgeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiKnowledgeEntryRepository extends JpaRepository<AiKnowledgeEntry, UUID> {

    List<AiKnowledgeEntry> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    // What the AI's retrieval step actually reads at chat time.
    List<AiKnowledgeEntry> findAllByBusinessIdAndActiveTrueOrderByCreatedAtDesc(UUID businessId);

    Optional<AiKnowledgeEntry> findByIdAndBusinessId(UUID id, UUID businessId);

    long countByBusinessId(UUID businessId);
}
