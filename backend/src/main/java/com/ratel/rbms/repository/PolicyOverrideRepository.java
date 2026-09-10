package com.ratel.rbms.repository;

import com.ratel.rbms.entity.PolicyOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PolicyOverrideRepository extends JpaRepository<PolicyOverride, UUID> {

    Optional<PolicyOverride> findByIdAndBusinessId(UUID id, UUID businessId);

    // The gate's own lookup: an APPROVED, unconsumed override for this exact blocking
    // requirement. Version-scoped like PolicyDisclosureRepository's own satisfaction query — an
    // override for an old policy version never satisfies a newer one.
    Optional<PolicyOverride> findByBusinessIdAndCommitmentReferenceAndPolicyIdAndPolicyVersionIdAndStatus(
            UUID businessId, UUID commitmentReference, UUID policyId, UUID policyVersionId, String status);

    // Conditional decision, checked by affected-row-count — the same discipline as
    // PendingApprovalService's own approve()/reject() flow, extended here to a WHERE-guarded
    // bulk statement so a losing concurrent decision attempt observes 0 rows affected rather
    // than silently overwriting the winner's decision. clearAutomatically: a bulk UPDATE bypasses
    // the persistence context (same Hibernate gotcha Phase 3's linkToTransaction hit and fixed) —
    // without this, a caller re-reading this same row later in the same transaction would see
    // the stale, pre-update PENDING status instead of the real one.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PolicyOverride o SET o.status = :newStatus, o.decidedBy = :decidedBy, "
            + "o.decidedAt = :decidedAt, o.decisionNote = :decisionNote "
            + "WHERE o.id = :id AND o.businessId = :businessId AND o.status = 'PENDING'")
    int decide(@Param("id") UUID id, @Param("businessId") UUID businessId,
               @Param("newStatus") String newStatus, @Param("decidedBy") UUID decidedBy,
               @Param("decidedAt") Instant decidedAt, @Param("decisionNote") String decisionNote);

    // Cancellation is allowed from PENDING (no decision was ever made) or APPROVED (decided, but
    // not yet consumed) — never from a terminal status. clearAutomatically: same reasoning as decide() above.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PolicyOverride o SET o.status = 'CANCELLED' "
            + "WHERE o.id = :id AND o.businessId = :businessId AND o.status IN ('PENDING', 'APPROVED')")
    int cancel(@Param("id") UUID id, @Param("businessId") UUID businessId);

    // Consumption — clearAutomatically like PolicyDisclosureRepository.linkToTransaction, for
    // the same reason: a bulk UPDATE bypasses the persistence context, so a caller re-reading
    // this same row later in the same transaction must see the fresh, post-update value.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PolicyOverride o SET o.status = 'CONSUMED', o.consumedAt = :consumedAt "
            + "WHERE o.id = :id AND o.businessId = :businessId AND o.status = 'APPROVED'")
    int consume(@Param("id") UUID id, @Param("businessId") UUID businessId, @Param("consumedAt") Instant consumedAt);

    // Bulk-expires every still-outstanding (PENDING/APPROVED) override tied to a commitment that
    // has just been INVALIDATED (Revision 4 §9, point 10) — an override for a stale requirement
    // must not remain silently claimable later.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PolicyOverride o SET o.status = 'EXPIRED' "
            + "WHERE o.businessId = :businessId AND o.commitmentReference = :commitmentReference "
            + "AND o.status IN ('PENDING', 'APPROVED')")
    int expireOutstanding(@Param("businessId") UUID businessId, @Param("commitmentReference") UUID commitmentReference);
}
