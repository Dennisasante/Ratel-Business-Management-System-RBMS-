package com.ratel.rbms.service;

import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.Option;
import com.ratel.rbms.entity.PackageComponent;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServicePackage;
import com.ratel.rbms.entity.ServicePackageItem;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.OptionRepository;
import com.ratel.rbms.repository.PackageComponentRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServicePackageItemRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5C — the narrow package-internals backfill (frozen design,
 * Revision 4 §3/§6): for every real {@link ServicePackageItem}, creates/reconciles exactly one
 * {@link PackageComponent} (required, min=1, max=1) and exactly one default {@link Option}, so
 * {@code PackagePricingService.calculate()} against a backfilled PACKAGE Offering reproduces
 * today's fixed legacy package price AND today's included-item display, not merely the total.
 *
 * <p>Deliberately a plain service, not a Flyway migration — same reasoning as
 * {@link OfferingBackfillService}, which this class is the direct sibling of (that class backfills
 * Offering/OfferingBookingConfig; this one backfills the package-internals that class explicitly
 * deferred). {@code dryRun} computes and reports without writing. Never auto-run.
 *
 * <p><b>Exact mapping (Revision 4 §3/§6), all defensively re-verified against fresh source during
 * Phase 5C implementation:</b>
 * <ul>
 *   <li>Cardinality: one {@code ServicePackageItem} -&gt; one {@code PackageComponent} -&gt; one
 *       {@code Option}. Idempotency key: {@code (business_id, offering_id,
 *       legacy_service_package_item_id)} (composite unique index, V59) — a rerun finds and
 *       reconciles the SAME component, never duplicates.</li>
 *   <li>Label: {@code ServicePackageItem} itself has NO stored label of its own (confirmed by
 *       fresh source read — only {@code id}/{@code packageId}/{@code serviceCatalogId}/
 *       {@code quantity}); legacy's own {@code BookingService.includedItemLabels} derives the
 *       display string LIVE from the linked {@code ServiceCatalogItem}'s CURRENT name (prefixed
 *       "Nx " when quantity &gt; 1). The canonical {@code Option.label} therefore reproduces that
 *       exact same live-derived string, re-synced on every rerun — matching legacy's own
 *       behaviour precisely, not a frozen-at-first-backfill snapshot (this is a correction from
 *       the design-phase assumption that {@code ServicePackageItem} carried its own stored label;
 *       fresh source proved otherwise, and reconciliation-on-rerun was already part of the
 *       approved design, so no architectural change was needed to accommodate it).</li>
 *   <li>Ordering: no existing legacy column captures true original entry sequence (no ordering
 *       column, no created_at on {@code ServicePackageItem}) — {@code displayOrder} is therefore
 *       assigned deterministically by legacy item id sort, stable/reproducible across reruns, but
 *       NOT a reconstruction of an order the legacy schema never captured. Documented as a known
 *       limitation, not silently invented.</li>
 *   <li>Required/min/max: always required=true, minSelections=1, maxSelections=1 — every legacy
 *       package item is mandatory by construction.</li>
 *   <li>Zero price adjustment, {@code offeringId=null}: the package's entire commercial value
 *       stays in {@code Offering.basePrice} — linking would double-count (Revision 4 §3/§6).</li>
 *   <li>Tenant/package ownership: {@code ServicePackageItem} has no {@code business_id} of its
 *       own — resolved transitively via {@code ServicePackageItem.packageId -&gt;
 *       ServicePackage.businessId} (fresh-source finding, Revision 4 §3). Cross-tenant/cross-
 *       package linkage is structurally prevented: items are only ever read scoped to ONE
 *       package (never a business-wide item query), and each item's own
 *       {@code serviceCatalogId} is independently re-verified against that SAME business as a
 *       defense-in-depth re-check of {@code ServicePackageService.saveItems}'s own existing
 *       tenant-scoped lookup.</li>
 * </ul>
 */
@Service
public class PackageContentBackfillService {

    public record ItemBackfillOutcome(UUID legacyItemId, UUID componentId, UUID optionId, String label, boolean created) {
    }

    public record PackageBackfillResult(
            UUID packageId,
            List<ItemBackfillOutcome> items,
            boolean dryRun
    ) {
    }

    private final ServicePackageRepository servicePackageRepository;
    private final ServicePackageItemRepository servicePackageItemRepository;
    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final OfferingRepository offeringRepository;
    private final PackageComponentRepository packageComponentRepository;
    private final OptionRepository optionRepository;

    public PackageContentBackfillService(
            ServicePackageRepository servicePackageRepository,
            ServicePackageItemRepository servicePackageItemRepository,
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            OfferingRepository offeringRepository,
            PackageComponentRepository packageComponentRepository,
            OptionRepository optionRepository
    ) {
        this.servicePackageRepository = servicePackageRepository;
        this.servicePackageItemRepository = servicePackageItemRepository;
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.offeringRepository = offeringRepository;
        this.packageComponentRepository = packageComponentRepository;
        this.optionRepository = optionRepository;
    }

    /**
     * Backfills (or dry-run reports) every package belonging to one business — the per-business
     * convenience entry point, mirroring {@code OfferingBackfillService.backfillBusiness}'s own
     * shape. A package with no canonical Offering mapping yet is skipped and reported separately
     * (its own Phase 5A backfill must run first) rather than failing the whole batch.
     */
    @Transactional
    public List<PackageBackfillResult> backfillBusiness(UUID businessId, boolean dryRun) {
        List<PackageBackfillResult> results = new ArrayList<>();
        for (ServicePackage pkg : servicePackageRepository.findAllByBusinessIdOrderByNameAsc(businessId)) {
            if (offeringRepository.findByBusinessIdAndLegacyServicePackageId(businessId, pkg.getId()).isEmpty()) {
                results.add(new PackageBackfillResult(pkg.getId(), List.of(), dryRun)); // no mapping yet — nothing to do
                continue;
            }
            results.add(backfillPackage(businessId, pkg.getId(), dryRun));
        }
        return results;
    }

    /**
     * Backfills (or dry-run reports) ONE package's component/option internals. Requires the
     * package to already have a canonical PACKAGE Offering mapping (Phase 5A's own backfill must
     * run first — Revision 4 §19, risk 5, "backfill ordering dependency").
     */
    @Transactional
    public PackageBackfillResult backfillPackage(UUID businessId, UUID packageId, boolean dryRun) {
        ServicePackage pkg = servicePackageRepository.findByIdAndBusinessId(packageId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Package not found."));
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(businessId, packageId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Package has no canonical Offering mapping yet — run the Phase 5A backfill first."));
        if (!"PACKAGE".equals(offering.getType())) {
            // Defensive only — OfferingSyncService.syncServicePackage always creates type=PACKAGE;
            // structurally unreachable, kept for the same forward-compatibility reason Phase 5B
            // keeps its own structurally-unreachable reason codes.
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mapped Offering is not a PACKAGE Offering.");
        }

        List<ServicePackageItem> items = servicePackageItemRepository.findAllByPackageId(packageId).stream()
                .sorted(Comparator.comparing(ServicePackageItem::getId))
                .toList();

        List<ItemBackfillOutcome> outcomes = new ArrayList<>();
        int order = 0;
        for (ServicePackageItem item : items) {
            outcomes.add(backfillOneItem(businessId, offering, item, order, dryRun));
            order++;
        }

        return new PackageBackfillResult(packageId, outcomes, dryRun);
    }

    private ItemBackfillOutcome backfillOneItem(UUID businessId, Offering offering, ServicePackageItem item, int order, boolean dryRun) {
        // Defense-in-depth re-check of ServicePackageService.saveItems' own existing tenant-scoped
        // lookup (Revision 4 §3) — catches any future legacy-layer regression rather than
        // silently propagating a cross-tenant reference into canonical data.
        ServiceCatalogItem catalogItem = serviceCatalogItemRepository.findByIdAndBusinessId(item.getServiceCatalogId(), businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "Package item " + item.getId() + " references a service catalog item outside this business — cannot backfill safely."));

        String label = item.getQuantity() > 1 ? item.getQuantity() + "x " + catalogItem.getName() : catalogItem.getName();

        PackageComponent existing = packageComponentRepository
                .findByBusinessIdAndOfferingIdAndLegacyServicePackageItemId(businessId, offering.getId(), item.getId())
                .orElse(null);

        if (existing != null) {
            Option option = existing.getDefaultOptionId() != null
                    ? optionRepository.findByIdAndBusinessId(existing.getDefaultOptionId(), businessId).orElse(null)
                    : null;
            boolean labelChanged = option != null && !label.equals(option.getLabel());
            boolean orderChanged = existing.getDisplayOrder() != order;
            if (!dryRun && (labelChanged || orderChanged)) {
                if (orderChanged) {
                    existing.setDisplayOrder(order);
                    packageComponentRepository.save(existing);
                }
                if (labelChanged) {
                    option.setLabel(label);
                    optionRepository.save(option);
                }
            }
            return new ItemBackfillOutcome(item.getId(), existing.getId(),
                    option != null ? option.getId() : null, label, false);
        }

        if (dryRun) {
            // Report what WOULD be created — no ids exist yet, since nothing is written.
            return new ItemBackfillOutcome(item.getId(), null, null, label, true);
        }

        // Slot name is an internal identifier, never customer-facing (Option.label carries the
        // display string) — deterministic and stable across reruns since it's derived from the
        // legacy item's own globally-unique id.
        String slotName = "legacy-item-" + item.getId();

        PackageComponent component = packageComponentRepository.save(PackageComponent.builder()
                .businessId(businessId)
                .offeringId(offering.getId())
                .slotName(slotName)
                .componentKind("SELECTION")
                .required(true)
                .minSelections(1)
                .maxSelections(1)
                .displayOrder(order)
                .legacyServicePackageItemId(item.getId())
                .build());

        Option option = optionRepository.save(Option.builder()
                .businessId(businessId)
                .componentId(component.getId())
                .offeringId(null) // never linked — see class comment: linking would double-count against basePrice
                .label(label)
                .priceAdjustment(BigDecimal.ZERO)
                .requiresManualQuote(false)
                .build());

        component.setDefaultOptionId(option.getId());
        component = packageComponentRepository.save(component);

        return new ItemBackfillOutcome(item.getId(), component.getId(), option.getId(), label, true);
    }

    /**
     * An orphan: a canonical component the backfill previously created whose source
     * {@code ServicePackageItem} no longer exists (deleted from the legacy package since).
     * Revision 4 §3/§6: excluded from {@code OfferingResolutionService.defaultSelections}, always
     * reported as {@code CONTENTS_MISMATCH} by verification, blocks that business/package from
     * cutover. Never silently deleted or ignored.
     */
    public List<PackageComponent> findOrphanComponents(UUID businessId, UUID offeringId) {
        return packageComponentRepository.findAllByBusinessIdAndOfferingId(businessId, offeringId).stream()
                .filter(c -> c.getLegacyServicePackageItemId() != null)
                .filter(c -> !servicePackageItemRepository.existsById(c.getLegacyServicePackageItemId()))
                .toList();
    }
}
