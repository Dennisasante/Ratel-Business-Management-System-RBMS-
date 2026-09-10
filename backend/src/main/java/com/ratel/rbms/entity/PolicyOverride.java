package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 4 — single-use, scoped evidence that an OWNER authorized a
 * specific staff member to proceed past one specific, otherwise-blocking policy version, for one
 * specific transaction attempt. Frozen design: Revision 4 §9 / Final Corrections §2.
 *
 * <p>Never a generic "manager approved" bypass — bound to the exact
 * {@code (commitment_reference, policy_id, policy_version_id)} tuple it authorizes (not "the
 * policy" in general), consumed exactly once by {@link com.ratel.rbms.service.PolicyEngine}'s
 * gate, and approvable only by OWNER — {@code Role.MANAGER} has no verified approval authority
 * anywhere in this codebase today (confirmed: {@code PendingApprovalController}'s own
 * {@code @PreAuthorize("hasRole('OWNER')")}), so this class does not invent one.
 *
 * <p>Deliberately NOT an extension of {@link PendingApproval}: that class's
 * {@code SourceType}/{@code ActionType} are closed enums scoped to applying one deferred domain
 * mutation to one row (see its own {@code applyChange} pattern) — a policy override isn't a
 * deferred mutation, it's evidence that lets one gate evaluation pass despite unmet requirements.
 * Bending {@code PendingApproval}'s shape to fit that would be less honest than a dedicated,
 * smaller table.
 */
@Entity
@Table(name = "policy_overrides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyOverride {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "commitment_reference", nullable = false)
    private UUID commitmentReference;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "policy_version_id", nullable = false)
    private UUID policyVersionId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private Instant requestedAt = Instant.now();

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING | APPROVED | REJECTED | CONSUMED | CANCELLED | EXPIRED

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "consumed_at")
    private Instant consumedAt;
}
