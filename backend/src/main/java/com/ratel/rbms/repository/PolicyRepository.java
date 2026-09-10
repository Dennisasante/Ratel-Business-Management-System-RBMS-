package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    // Applicable policies for a given action: active, and either business-wide
    // (offeringId IS NULL on the policy) or scoped to the specific offering in play.
    // :offeringId may itself be null (the action isn't tied to any specific offering) — in
    // that case only business-wide policies match, which p.offeringId IS NULL already covers.
    @Query("SELECT p FROM Policy p WHERE p.businessId = :businessId AND p.appliesToAction = :appliesToAction "
            + "AND p.active = true AND (p.offeringId IS NULL OR p.offeringId = :offeringId)")
    List<Policy> findApplicable(@Param("businessId") UUID businessId,
                                 @Param("appliesToAction") String appliesToAction,
                                 @Param("offeringId") UUID offeringId);
}
