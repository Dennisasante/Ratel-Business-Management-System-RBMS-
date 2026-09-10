package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 3/4 — registers one commitment attempt (e.g. one specific
 * booking flow, before the real transaction row exists) to exactly one business, permanently.
 * Minted only by {@code PolicyEngine.beginCommitment(...)} — never caller-supplied — which is
 * what makes cross-business reuse of a commitment reference a real constraint violation
 * ({@link PolicyDisclosure}'s own composite FK against this table) rather than something a
 * caller is merely trusted to get right.
 *
 * <p>Phase 4 (Revision 4 §1, frozen design): this row does NOT attempt to snapshot the
 * transaction. Its purpose is to bind one policy-evidence lifecycle to one single-use
 * transaction attempt — it carries only {@code transactionType} (what kind of attempt) and its
 * own {@code status}/timestamps (the attempt's own lifecycle), never customer/offering/quantity,
 * which already have one authoritative owner elsewhere ({@link PolicyDisclosure}, the real
 * transaction row). See {@link com.ratel.rbms.service.PolicyEngine} for the state machine.
 */
@Entity
@Table(name = "policy_commitments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyCommitment {

    @Id
    @Column(name = "commitment_reference")
    @Builder.Default
    private UUID commitmentReference = UUID.randomUUID();

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    // Nullable at open time — bound on first use by PolicyEngine.evaluateGate() if not supplied
    // at beginCommitment(). Once bound, immutable in practice: evaluateGate() rejects a mismatch
    // rather than rebinding (Revision 4 §2 — a commitment binds to exactly one transaction type).
    @Column(name = "transaction_type", length = 30)
    private String transactionType;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE | COMPLETED | ABANDONED | INVALIDATED

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
