package com.ratel.rbms.repository;

import com.ratel.rbms.entity.PolicyCommitment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PolicyCommitmentRepository extends JpaRepository<PolicyCommitment, UUID> {

    // Tenant-scoped lookup — never resolve a commitment by its reference alone; always pair it
    // with the caller's own asserted businessId, same discipline as every other tenant-scoped
    // lookup in this codebase (findByIdAndBusinessId(...) elsewhere).
    Optional<PolicyCommitment> findByCommitmentReferenceAndBusinessId(UUID commitmentReference, UUID businessId);

    // Phase 4 (Revision 4 §2) — locks the row for the rest of the transaction, same pattern and
    // same purpose as BusinessRepository.findByIdForUpdate (used by BillingService.verifyPayment
    // to stop a concurrent webhook + client-triggered verify from both reading a stale value and
    // independently extending it): PolicyEngine.evaluateGate() uses this so two concurrent gate
    // evaluations against the same commitment_reference (e.g. racing to bind transaction_type,
    // or racing to complete the commitment) can't both observe pre-write state and diverge. Must
    // be called from inside the caller's own @Transactional method — Postgres row locks are held
    // for the rest of that transaction, not just this one statement.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PolicyCommitment c WHERE c.commitmentReference = :commitmentReference AND c.businessId = :businessId")
    Optional<PolicyCommitment> findByCommitmentReferenceForUpdate(
            @Param("commitmentReference") UUID commitmentReference, @Param("businessId") UUID businessId);
}
