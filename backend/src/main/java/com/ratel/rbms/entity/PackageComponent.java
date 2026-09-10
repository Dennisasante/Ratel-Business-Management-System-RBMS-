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
 * Talia Unified Platform, Phase 2 — a named attachment point on one
 * {@link Offering}. Two kinds, discriminated by {@code componentKind}
 * (mirrors this project's own {@code SaleItem.itemType} precedent):
 *
 * <p><b>SELECTION</b> — a slot or modifier group governed by
 * {@code required}/{@code minSelections}/{@code maxSelections} over its
 * child {@link Option} rows. The same three fields describe a mandatory
 * single pick (a package's protein slot: required=true, min=1, max=1), an
 * optional multi-pick (pizza toppings: required=false, min=0, max=null),
 * and an optional single toggle (pool access: required=false, min=0,
 * max=1) — one real invariant, different parameter values, not three
 * different structures.
 *
 * <p><b>QUANTITY</b> — an anchor for a sibling {@link QuantityRule} row
 * instead (e.g. a birthday package's guest count). Its own
 * required/minSelections/maxSelections are not meaningful for this kind —
 * the database itself refuses a QUANTITY-kind row carrying SELECTION-kind
 * values (see the migration's own
 * chk_package_components_quantity_kind_bounds).
 *
 * <p>Whether a given component actually has a matching QuantityRule row
 * (for QUANTITY) or none (for SELECTION) is <b>not</b> enforced at the
 * database level — that would need a cross-table check Postgres can't
 * express without a trigger, and Phase 2 stays trigger-free. A future
 * service layer must enforce this.
 *
 * <p>Phase 5B: {@code defaultOptionId} (nullable) names the option that is already priced into
 * the owning Offering's {@code basePrice} — meaningful only on a component that is both
 * {@code required} and capped at exactly one selection (DB-enforced,
 * chk_package_components_default_option_requires_single_required). Selecting the default itself
 * is priced normally; selecting anything else requires a matching, same-component
 * {@link SubstitutionRule}. See {@code service/PackagePricingService.java} for the full
 * calculation this drives.
 */
@Entity
@Table(name = "package_components")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackageComponent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "offering_id", nullable = false)
    private UUID offeringId;

    @Column(name = "slot_name", nullable = false, length = 100)
    private String slotName;

    // SELECTION or QUANTITY — DB-constrained, see chk_package_components_kind.
    @Column(name = "component_kind", nullable = false, length = 20)
    @Builder.Default
    private String componentKind = "SELECTION";

    @Column(nullable = false)
    @Builder.Default
    private boolean required = false;

    @Column(name = "min_selections", nullable = false)
    @Builder.Default
    private int minSelections = 0;

    // Null = unlimited.
    @Column(name = "max_selections")
    private Integer maxSelections;

    // Null = no default (every explicit selection is an ordinary selection, never a
    // substitution). Non-null only valid when required=true and maxSelections=1 (DB-enforced).
    @Column(name = "default_option_id")
    private UUID defaultOptionId;

    // Phase 5C: deterministic display order, assigned by the package-content backfill (no
    // existing legacy column captures original ServicePackageItem entry sequence — see
    // PackageContentBackfillService's own class comment). Zero for any component not produced by
    // that backfill.
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    // Phase 5C: the package-content backfill's own idempotency/reconciliation key — set only on a
    // component the backfill created; null for anything else. See
    // service/PackageContentBackfillService.java.
    @Column(name = "legacy_service_package_item_id")
    private UUID legacyServicePackageItemId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
