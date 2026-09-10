package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5C — the immutable, engine-agnostic historical transaction
 * snapshot (frozen design, Revision 4 §2/§6). One row per commercial line contributing to a
 * canonically-priced {@link ServiceOrder}'s total.
 *
 * <p>Deliberately NOT a persisted copy of {@code PricingResult}/{@code PricingAdjustment} (Phase
 * 5B's own pricing-engine diagnostic shape, which is free to change as configurable-package
 * semantics evolve later) — {@code label}/{@code amount}/{@code quantity} are the commercial
 * facts any pricing model can always produce. {@code sourceComponentId}/{@code sourceOptionId}
 * are nullable traceability pointers ONLY, never re-read for pricing or display — see the
 * migration's own {@code ON DELETE SET NULL} (Model A: historical transaction survives catalog
 * deletion).
 *
 * <p>Enforced append-only at the database level — see the migration's
 * {@code trg_snapshot_append_only} trigger. This entity's own fields are therefore never mutated
 * by application code after {@link com.ratel.rbms.repository.ServiceOrderLineSnapshotRepository}
 * first saves a row; a correction is a future, separate, additive mechanism
 * ({@code service_order_line_corrections} — explicitly out of scope for Phase 5C).
 */
@Entity
@Table(name = "service_order_line_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceOrderLineSnapshot {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "service_order_id", nullable = false)
    private UUID serviceOrderId;

    @Column(nullable = false, length = 150)
    private String label;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column
    private Integer quantity;

    @Column(name = "source_component_id")
    private UUID sourceComponentId;

    @Column(name = "source_option_id")
    private UUID sourceOptionId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
