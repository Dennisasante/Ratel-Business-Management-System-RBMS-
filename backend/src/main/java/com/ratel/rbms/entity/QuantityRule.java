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
 * Talia Unified Platform, Phase 2 — overage pricing for a QUANTITY-kind
 * {@link PackageComponent} (e.g. a birthday package's base 20 guests,
 * +GH80 per extra guest beyond that). At most one row per component (see
 * the migration's own uq_quantity_rules_component) — a component needing
 * tiered/multi-band quantity pricing is future scope, not one of this
 * phase's worked examples.
 */
@Entity
@Table(name = "quantity_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuantityRule {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    @Column(name = "min_quantity", nullable = false)
    @Builder.Default
    private int minQuantity = 0;

    // Null = unlimited.
    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Column(name = "extra_unit_price", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal extraUnitPrice = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
