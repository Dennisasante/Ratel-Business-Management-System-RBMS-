package com.ratel.rbms.repository;

import com.ratel.rbms.entity.SubstitutionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubstitutionRuleRepository extends JpaRepository<SubstitutionRule, UUID> {

    // uq_substitution_rules_pair guarantees at most one row per (from, to) — this is the one
    // lookup PackagePricingService needs: "is this specific swap permitted, and at what price."
    Optional<SubstitutionRule> findByBusinessIdAndFromOptionIdAndToOptionId(UUID businessId, UUID fromOptionId, UUID toOptionId);

    // Every rule rooted at a given default option — used by PackagePricingService to proactively
    // verify catalog integrity for a defaulted component (every rule's "to" side must actually
    // belong to that same component), independent of what a specific customer selects.
    List<SubstitutionRule> findAllByBusinessIdAndFromOptionId(UUID businessId, UUID fromOptionId);
}
