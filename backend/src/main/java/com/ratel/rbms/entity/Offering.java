package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 2 — the generic sellable/bookable thing
 * (architecture proposal §F). Purely additive: no existing entity/table is
 * changed, and no service/controller reads this yet. See
 * {@link PackageComponent}'s own class comment for how slots, modifier
 * groups, and quantity anchors attach to an Offering.
 *
 * <p>Phase 5A additions: {@code active} is the identity-level retirement flag
 * (mirrors {@code Policy.active}) — meaningful across every transaction type,
 * unlike {@link OfferingBookingConfig}'s scheduling-only fields.
 * {@code legacyServiceCatalogId}/{@code legacyServicePackageId} are the
 * explicit, typed, deterministic link to this Offering's legacy source
 * during coexistence — never both set (DB-enforced), never inferred by name.
 * See {@code service/OfferingResolutionService.java} for the one approved
 * way to resolve a legacy item to its Offering.
 */
@Entity
@Table(name = "offerings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Offering {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    // PRODUCT / SERVICE / PACKAGE / ROOM_TYPE / EVENT_PACKAGE — DB-constrained,
    // see the migration's own chk_offerings_type.
    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal basePrice = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    // Mutually exclusive with legacyServicePackageId (DB-enforced,
    // chk_offerings_legacy_mutually_exclusive). Null = no legacy counterpart.
    @Column(name = "legacy_service_catalog_id")
    private UUID legacyServiceCatalogId;

    @Column(name = "legacy_service_package_id")
    private UUID legacyServicePackageId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
