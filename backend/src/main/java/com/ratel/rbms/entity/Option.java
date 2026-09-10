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
 * Talia Unified Platform, Phase 2 — a concrete fill for a SELECTION-kind
 * {@link PackageComponent}'s slot, or ({@code componentId} null) a
 * standalone add-on with no owning component. {@code priceAdjustment} is
 * deliberately signed and unconstrained — mirrors
 * {@code CustomItemAttributeOption.priceModifier}'s own existing
 * precedent; a downgrade/discount option is legitimate.
 * {@code businessId} carries its own direct FK to {@code businesses} (not
 * just the composite FK to {@code package_components}) specifically so a
 * standalone option's ownership stays unambiguous even when
 * {@code componentId} is null.
 *
 * <p>Phase 5A: {@code offeringId} (nullable) lets this Option point at a
 * real, independently-priced {@link Offering} — reproducing
 * {@code ServicePackageItem}'s own job (a package slot pointing at a real
 * catalog service) without redefining that service. Only SERVICE-type
 * Offerings may be referenced — deliberately enforced at the service layer,
 * not the database, matching this schema's existing trigger-free posture for
 * cross-table semantic invariants (see {@code PackageComponent}'s own class
 * comment). {@code priceAdjustment} is a delta relative to the referenced
 * Offering's current price, never an absolute replacement. When
 * {@code offeringId} is null, this row keeps its original, independent
 * label/price-modifier meaning unchanged.
 */
@Entity
@Table(name = "options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Option {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    // Null = a standalone add-on/modifier, not tied to a package slot.
    @Column(name = "component_id")
    private UUID componentId;

    // Null = no linked canonical Offering (standalone label/price-modifier, unchanged Phase 2
    // meaning). Non-null = this Option represents a real, independently-priced SERVICE Offering.
    @Column(name = "offering_id")
    private UUID offeringId;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "price_adjustment", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal priceAdjustment = BigDecimal.ZERO;

    @Column(name = "requires_manual_quote", nullable = false)
    @Builder.Default
    private boolean requiresManualQuote = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
