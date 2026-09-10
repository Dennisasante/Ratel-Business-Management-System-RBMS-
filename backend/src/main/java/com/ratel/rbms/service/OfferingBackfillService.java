package com.ratel.rbms.service;

import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServicePackage;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5A — controlled, reviewable, one-time backfill of the canonical
 * Offering/OfferingBookingConfig representation for every existing legacy item. Deliberately a
 * plain service method, NOT a Flyway data migration — a real-data migration baked into the
 * Flyway chain forever is hard to independently validate or safely re-run; this class supports a
 * {@code dryRun} pass (reports what would happen, writes nothing) before any real execution.
 *
 * <p>Never auto-run (no {@code @PostConstruct}/{@code ApplicationRunner} anywhere in this class),
 * never invoked against production by this phase. Reuses {@link OfferingSyncService}'s exact
 * upsert logic for the actual writes — backfill is simply "call the same sync method used for a
 * live create, for every currently-unmapped legacy item" — deliberately not a second, parallel
 * creation code path.
 *
 * <p>Deliberately does NOT populate {@code PackageComponent}/{@code Option} rows for existing
 * packages' items — approved, explicit Phase 5A scope decision, deferred to Phase 5B pending the
 * substitution/quantity semantics design. A backfilled PACKAGE Offering is structurally valid
 * (has an Offering + OfferingBookingConfig row) but has zero package_components until then.
 */
@Service
public class OfferingBackfillService {

    /** One business's backfill outcome — same shape whether dryRun or a real run. */
    public record BackfillResult(
            List<UUID> serviceCatalogItemsMapped,
            List<UUID> serviceCatalogItemsAlreadyMapped,
            List<UUID> servicePackagesMapped,
            List<UUID> servicePackagesAlreadyMapped,
            boolean dryRun
    ) {
    }

    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final OfferingRepository offeringRepository;
    private final OfferingSyncService offeringSyncService;

    public OfferingBackfillService(
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            ServicePackageRepository servicePackageRepository,
            OfferingRepository offeringRepository,
            OfferingSyncService offeringSyncService
    ) {
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.servicePackageRepository = servicePackageRepository;
        this.offeringRepository = offeringRepository;
        this.offeringSyncService = offeringSyncService;
    }

    /**
     * Backfills one business. {@code dryRun = true} computes and returns exactly what would be
     * mapped without writing anything — the required "validate before executing" step. Backfills
     * BOTH active and inactive legacy items (so a later reactivation finds an existing mapping
     * rather than creating a duplicate) — the stricter "zero unmapped ACTIVE records" validation
     * gate (see {@link #findUnmappedActiveServiceCatalogItems}/{@link #findUnmappedActiveServicePackages})
     * is what actually gates production execution, per the approved specification.
     */
    @Transactional
    public BackfillResult backfillBusiness(UUID businessId, boolean dryRun) {
        List<UUID> catalogMapped = new ArrayList<>();
        List<UUID> catalogAlreadyMapped = new ArrayList<>();
        for (ServiceCatalogItem item : serviceCatalogItemRepository.findAllByBusinessIdOrderByNameAsc(businessId)) {
            boolean alreadyMapped = offeringRepository
                    .findByBusinessIdAndLegacyServiceCatalogId(businessId, item.getId()).isPresent();
            if (alreadyMapped) {
                catalogAlreadyMapped.add(item.getId());
                continue;
            }
            if (!dryRun) {
                offeringSyncService.syncServiceCatalogItem(item);
            }
            catalogMapped.add(item.getId());
        }

        List<UUID> packageMapped = new ArrayList<>();
        List<UUID> packageAlreadyMapped = new ArrayList<>();
        for (ServicePackage pkg : servicePackageRepository.findAllByBusinessIdOrderByNameAsc(businessId)) {
            boolean alreadyMapped = offeringRepository
                    .findByBusinessIdAndLegacyServicePackageId(businessId, pkg.getId()).isPresent();
            if (alreadyMapped) {
                packageAlreadyMapped.add(pkg.getId());
                continue;
            }
            if (!dryRun) {
                offeringSyncService.syncServicePackage(pkg);
            }
            packageMapped.add(pkg.getId());
        }

        return new BackfillResult(catalogMapped, catalogAlreadyMapped, packageMapped, packageAlreadyMapped, dryRun);
    }

    // ==================== Validation (pre-flight and post-run) ====================

    /** Every ACTIVE ServiceCatalogItem with no linked Offering — must be empty before production execution. */
    public List<UUID> findUnmappedActiveServiceCatalogItems(UUID businessId) {
        return serviceCatalogItemRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .filter(ServiceCatalogItem::isActive)
                .filter(item -> offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(businessId, item.getId()).isEmpty())
                .map(ServiceCatalogItem::getId)
                .toList();
    }

    /** Every ACTIVE ServicePackage with no linked Offering — must be empty before production execution. */
    public List<UUID> findUnmappedActiveServicePackages(UUID businessId) {
        return servicePackageRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .filter(ServicePackage::isActive)
                .filter(pkg -> offeringRepository.findByBusinessIdAndLegacyServicePackageId(businessId, pkg.getId()).isEmpty())
                .map(ServicePackage::getId)
                .toList();
    }
}
