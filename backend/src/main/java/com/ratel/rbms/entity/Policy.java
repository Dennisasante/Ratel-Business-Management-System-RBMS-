package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 3 — a policy's stable identity and routing configuration.
 * Deliberately holds no enforcement semantics (requiresAcknowledgement/blocksTransaction) —
 * those live on {@link PolicyVersion} instead, so a later edit here can never retroactively
 * change what a past disclosure actually enforced. Retirement is {@code active = false}; this
 * row (and every version under it) is never hard-deleted — see the migration's own
 * {@code ON DELETE RESTRICT} throughout this chain.
 */
@Entity
@Table(name = "policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Policy {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "policy_key", nullable = false, length = 100)
    private String policyKey;

    @Column(name = "applies_to_action", nullable = false, length = 60)
    private String appliesToAction;

    // Null = applies business-wide for appliesToAction. Non-null = scoped to one specific Offering.
    @Column(name = "offering_id")
    private UUID offeringId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
