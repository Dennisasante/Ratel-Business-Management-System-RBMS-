package com.ratel.rbms.repository;

import com.ratel.rbms.entity.QuantityRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuantityRuleRepository extends JpaRepository<QuantityRule, UUID> {

    // uq_quantity_rules_component guarantees at most one row per component.
    Optional<QuantityRule> findByBusinessIdAndComponentId(UUID businessId, UUID componentId);
}
