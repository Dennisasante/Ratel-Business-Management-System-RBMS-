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
 * Talia Unified Platform, Phase 3 — one immutable, append-only snapshot of a {@link Policy}'s
 * disclosed text AND the enforcement semantics that were actually in force for every
 * {@link PolicyDisclosure} pointing at it. Genuinely append-only: the database itself rejects
 * any UPDATE or DELETE on this table (see the migration's own
 * {@code reject_policy_version_mutation} trigger) — not merely a service-level convention.
 * Editing a policy is only ever expressed as inserting a new row with
 * {@code versionNumber + 1}; this class exposes no update path, matching that discipline.
 *
 * <p>"Current version" for a policy is deliberately not a stored pointer on {@link Policy}
 * (which would be a second place that could drift out of sync) — it's derived by querying the
 * highest {@code versionNumber} for a given {@code policyId}.
 */
@Entity
@Table(name = "policy_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyVersion {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "requires_acknowledgement", nullable = false)
    @Builder.Default
    private boolean requiresAcknowledgement = true;

    @Column(name = "blocks_transaction", nullable = false)
    @Builder.Default
    private boolean blocksTransaction = true;

    @Column(name = "staff_overridable", nullable = false)
    @Builder.Default
    private boolean staffOverridable = false;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
