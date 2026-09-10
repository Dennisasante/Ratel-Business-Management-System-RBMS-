package com.ratel.rbms.repository;

import com.ratel.rbms.entity.PolicyDisclosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyDisclosureRepository extends JpaRepository<PolicyDisclosure, UUID> {

    // Tenant-scoped — see PolicyCommitmentRepository's own comment on why businessId is always
    // paired with an id, never trusted from the id alone.
    Optional<PolicyDisclosure> findByIdAndBusinessId(UUID id, UUID businessId);

    // Has THIS specific policy VERSION already been acknowledged for THIS specific commitment
    // attempt? Scoped by commitment_reference, not customer/conversation — see Phase 3's own
    // review thread for why conversation-level scoping is insufficient (two transactions in one
    // conversation must not cross-satisfy each other). Version-scoped as of Phase 4 (Revision 4
    // §2 / Revision 3 Issue 2): the original Phase 3 query filtered by policyId alone, which
    // meant an acknowledgement of an OLD version would still be found and treated as satisfying
    // the CURRENT version after a policy edit — a real, source-confirmed gap, fixed here by
    // adding policyVersionId to the filter. A newer version simply has no matching rows until a
    // fresh disclosure/acknowledgement is recorded against it.
    List<PolicyDisclosure> findAllByBusinessIdAndPolicyIdAndPolicyVersionIdAndCommitmentReference(
            UUID businessId, UUID policyId, UUID policyVersionId, UUID commitmentReference);

    // The backfill step: once the real transaction exists, link every disclosure written under
    // this commitment attempt to it in one atomic statement. Tenant-scoped in the WHERE clause
    // itself, not just in the caller's own discipline.
    // clearAutomatically: a bulk UPDATE like this operates directly at the SQL level and does
    // NOT refresh any already-loaded PolicyDisclosure instances still held in the persistence
    // context — without this, a caller that re-reads the same row later in the same transaction
    // would see the stale, pre-update values. Clearing forces a genuine re-query.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PolicyDisclosure d SET d.transactionType = :transactionType, d.transactionId = :transactionId "
            + "WHERE d.businessId = :businessId AND d.commitmentReference = :commitmentReference")
    int linkToTransaction(@Param("businessId") UUID businessId,
                           @Param("commitmentReference") UUID commitmentReference,
                           @Param("transactionType") String transactionType,
                           @Param("transactionId") UUID transactionId);
}
