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
 * Talia Unified Platform, Phase 2 — "Option A may be substituted for
 * Option B, at this price delta, at most this many times" (e.g. Chicken
 * to Fish, +GH20, max 1). Both {@code fromOptionId} and {@code toOptionId}
 * are independently composite-FK'd to {@code options(business_id, id)}
 * against this row's own {@code businessId} — since both must equal the
 * same single anchor, this transitively guarantees they belong to the
 * same business as each other, without ever comparing the two options
 * directly.
 */
@Entity
@Table(name = "substitution_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubstitutionRule {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "from_option_id", nullable = false)
    private UUID fromOptionId;

    @Column(name = "to_option_id", nullable = false)
    private UUID toOptionId;

    @Column(name = "price_delta", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal priceDelta = BigDecimal.ZERO;

    @Column(name = "max_count", nullable = false)
    @Builder.Default
    private int maxCount = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
