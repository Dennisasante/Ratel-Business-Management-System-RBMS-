package com.ratel.rbms.repository;

import com.ratel.rbms.entity.ModuleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Phase 1 read model only — no service layer yet, since nothing in the
 * application consults this table's contents today. See ModuleDefinition's
 * own class comment.
 */
public interface ModuleDefinitionRepository extends JpaRepository<ModuleDefinition, String> {
}
