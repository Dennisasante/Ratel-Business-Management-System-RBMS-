package com.ratel.rbms.service;

import com.ratel.rbms.entity.*;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5A — proves {@link OfferingBackfillService} against real
 * PostgreSQL: a business with zero legacy records ("fresh", scoped at the business level — see
 * the implementation report for the separate literal fresh-database migration verification), a
 * populated business with active/inactive catalog items and packages, multiple businesses (tenant
 * isolation), already-mapped records (idempotency — never a second Offering), and dry-run vs. real
 * execution. @Transactional rolls every test back.
 */
@SpringBootTest
@Transactional
class Phase5ABackfillTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private ServicePackageRepository servicePackageRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private OfferingBookingConfigRepository offeringBookingConfigRepository;
    @Autowired private OfferingBackfillService offeringBackfillService;
    @Autowired private OfferingSyncService offeringSyncService;

    private Business newBusiness() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return businessRepository.save(Business.builder()
                .name("Phase 5A Backfill Test Business " + unique).slug("phase5a-backfill-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
    }

    private ServiceType newServiceType(UUID businessId) {
        return serviceTypeRepository.save(ServiceType.builder().businessId(businessId).name("Haircut").build());
    }

    private ServiceCatalogItem newCatalogItem(UUID businessId, UUID serviceTypeId, boolean active) {
        return serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(businessId).serviceTypeId(serviceTypeId).name("Item-" + UUID.randomUUID().toString().substring(0, 6))
                .price(new BigDecimal("50.00")).active(active).build());
    }

    private ServicePackage newPackage(UUID businessId, UUID serviceTypeId, boolean active) {
        return servicePackageRepository.save(ServicePackage.builder()
                .businessId(businessId).serviceTypeId(serviceTypeId).name("Package-" + UUID.randomUUID().toString().substring(0, 6))
                .price(new BigDecimal("500.00")).active(active).build());
    }

    @Test
    void backfillOnABusinessWithNoLegacyRecordsIsANoOp() {
        Business business = newBusiness();

        OfferingBackfillService.BackfillResult result = offeringBackfillService.backfillBusiness(business.getId(), false);

        assertTrue(result.serviceCatalogItemsMapped().isEmpty());
        assertTrue(result.servicePackagesMapped().isEmpty());
        assertTrue(offeringBackfillService.findUnmappedActiveServiceCatalogItems(business.getId()).isEmpty());
        assertTrue(offeringBackfillService.findUnmappedActiveServicePackages(business.getId()).isEmpty());
    }

    @Test
    void dryRunReportsWithoutWritingAnything() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newCatalogItem(business.getId(), type.getId(), true);

        OfferingBackfillService.BackfillResult result = offeringBackfillService.backfillBusiness(business.getId(), true);

        assertEquals(1, result.serviceCatalogItemsMapped().size());
        assertTrue(result.dryRun());
        assertTrue(offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.getId()).isEmpty(),
                "a dry run must never write an Offering row");
    }

    @Test
    void realRunMapsActiveAndInactiveItemsAndPackagesWithExactFieldParity() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem activeItem = newCatalogItem(business.getId(), type.getId(), true);
        ServiceCatalogItem inactiveItem = newCatalogItem(business.getId(), type.getId(), false);
        ServicePackage activePkg = newPackage(business.getId(), type.getId(), true);
        ServicePackage inactivePkg = newPackage(business.getId(), type.getId(), false);

        OfferingBackfillService.BackfillResult result = offeringBackfillService.backfillBusiness(business.getId(), false);

        assertEquals(2, result.serviceCatalogItemsMapped().size());
        assertEquals(2, result.servicePackagesMapped().size());

        Offering activeOffering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), activeItem.getId()).orElseThrow();
        assertEquals(activeItem.getName(), activeOffering.getName());
        assertEquals(0, activeItem.getPrice().compareTo(activeOffering.getBasePrice()));
        assertTrue(activeOffering.isActive());

        Offering inactiveOffering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), inactiveItem.getId()).orElseThrow();
        assertFalse(inactiveOffering.isActive(), "an inactive legacy item must backfill to an inactive Offering, not be skipped");

        Offering activePkgOffering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(business.getId(), activePkg.getId()).orElseThrow();
        assertEquals("PACKAGE", activePkgOffering.getType());

        // requiresLocation documented default for backfilled packages.
        OfferingBookingConfig pkgConfig = offeringBookingConfigRepository
                .findByOfferingIdAndBusinessId(activePkgOffering.getId(), business.getId()).orElseThrow();
        assertFalse(pkgConfig.isRequiresLocation());

        // The strict production-execution gate: zero unmapped ACTIVE records after a real run.
        assertTrue(offeringBackfillService.findUnmappedActiveServiceCatalogItems(business.getId()).isEmpty());
        assertTrue(offeringBackfillService.findUnmappedActiveServicePackages(business.getId()).isEmpty());
    }

    @Test
    void backfillNeverCreatesASecondOfferingForAnAlreadyMappedItem_idempotent() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newCatalogItem(business.getId(), type.getId(), true);

        offeringBackfillService.backfillBusiness(business.getId(), false);
        OfferingBackfillService.BackfillResult second = offeringBackfillService.backfillBusiness(business.getId(), false);

        assertTrue(second.serviceCatalogItemsMapped().isEmpty(), "a second run must map nothing new");
        assertEquals(1, second.serviceCatalogItemsAlreadyMapped().size());

        long offeringCount = offeringRepository.findAll().stream()
                .filter(o -> item.getId().equals(o.getLegacyServiceCatalogId())).count();
        assertEquals(1, offeringCount, "exactly one Offering must exist for this legacy item, never two");
    }

    @Test
    void backfillRespectsTenantIsolationAcrossMultipleBusinesses() {
        Business a = newBusiness();
        Business b = newBusiness();
        ServiceType typeA = newServiceType(a.getId());
        ServiceType typeB = newServiceType(b.getId());
        ServiceCatalogItem itemA = newCatalogItem(a.getId(), typeA.getId(), true);
        ServiceCatalogItem itemB = newCatalogItem(b.getId(), typeB.getId(), true);

        offeringBackfillService.backfillBusiness(a.getId(), false);
        offeringBackfillService.backfillBusiness(b.getId(), false);

        Offering offeringA = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(a.getId(), itemA.getId()).orElseThrow();
        Offering offeringB = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(b.getId(), itemB.getId()).orElseThrow();
        assertEquals(a.getId(), offeringA.getBusinessId());
        assertEquals(b.getId(), offeringB.getBusinessId());
        // b's item must never resolve under a's business scope.
        assertTrue(offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(a.getId(), itemB.getId()).isEmpty());
    }

    @Test
    void unmappedActiveRecordDetectionIsExactAfterAPartialSync() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem synced = newCatalogItem(business.getId(), type.getId(), true);
        ServiceCatalogItem unsynced = newCatalogItem(business.getId(), type.getId(), true);
        offeringSyncService.syncServiceCatalogItem(synced); // simulate: created via the live sync path, not backfill

        List<UUID> unmapped = offeringBackfillService.findUnmappedActiveServiceCatalogItems(business.getId());
        assertEquals(List.of(unsynced.getId()), unmapped);

        // Running the backfill afterward must map only the genuinely-unmapped one.
        OfferingBackfillService.BackfillResult result = offeringBackfillService.backfillBusiness(business.getId(), false);
        assertEquals(1, result.serviceCatalogItemsMapped().size());
        assertEquals(unsynced.getId(), result.serviceCatalogItemsMapped().get(0));
        assertEquals(1, result.serviceCatalogItemsAlreadyMapped().size());
        assertTrue(offeringBackfillService.findUnmappedActiveServiceCatalogItems(business.getId()).isEmpty());
    }
}
