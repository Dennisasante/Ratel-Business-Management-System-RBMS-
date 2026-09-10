package com.ratel.rbms.service;

import com.ratel.rbms.dto.ServiceCatalogItemRequest;
import com.ratel.rbms.dto.ServiceCatalogItemResponse;
import com.ratel.rbms.dto.ServicePackageItemRequest;
import com.ratel.rbms.dto.ServicePackageRequest;
import com.ratel.rbms.dto.ServicePackageResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.PackageComponent;
import com.ratel.rbms.entity.ServicePackageItem;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.OfferingBookingConfigRepository;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.OptionRepository;
import com.ratel.rbms.repository.PackageComponentRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServicePackageItemRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5C — real-PostgreSQL proof of the narrow package-internals
 * backfill's exact invariants (frozen design, Revision 4 §3/§6). Each test method owns its own
 * business/package fixture (created and torn down within the method, or relying on the shared
 * per-test @Transactional rollback where noted) so tests never interfere with each other.
 */
@SpringBootTest
class PackageContentBackfillServiceTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private ServicePackageRepository servicePackageRepository;
    @Autowired private ServicePackageItemRepository servicePackageItemRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private OfferingBookingConfigRepository offeringBookingConfigRepository;
    @Autowired private PackageComponentRepository packageComponentRepository;
    @Autowired private OptionRepository optionRepository;
    @Autowired private ServiceCatalogService serviceCatalogService;
    @Autowired private ServicePackageService servicePackageService;
    @Autowired private OfferingBackfillService offeringBackfillService;
    @Autowired private PackageContentBackfillService packageContentBackfillService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private Business newBusiness(String label) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return businessRepository.save(Business.builder()
                .name("Phase5C Backfill " + label + " " + unique)
                .slug("phase5c-backfill-" + label.toLowerCase().replace(" ", "-") + "-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
    }

    private void teardown(UUID businessId) {
        offeringBookingConfigRepository.findAll().stream()
                .filter(c -> c.getBusinessId().equals(businessId))
                .forEach(c -> offeringBookingConfigRepository.deleteById(c.getOfferingId()));
        // Delete PackageComponent FIRST — fk_options_component (options.component_id ->
        // package_components) is ON DELETE CASCADE, so this takes every backfilled Option with
        // it automatically. Deleting options first would instead hit
        // fk_package_components_default_option (RESTRICT), since a component's own
        // default_option_id still points at it.
        packageComponentRepository.deleteAll(packageComponentRepository.findAll().stream().filter(c -> c.getBusinessId().equals(businessId)).toList());
        offeringRepository.deleteAll(offeringRepository.findAll().stream().filter(o -> o.getBusinessId().equals(businessId)).toList());
        servicePackageItemRepository.deleteAll(servicePackageItemRepository.findAll().stream()
                .filter(i -> servicePackageRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream().anyMatch(p -> p.getId().equals(i.getPackageId())))
                .toList());
        servicePackageRepository.deleteAll(servicePackageRepository.findAllByBusinessIdOrderByNameAsc(businessId));
        serviceCatalogItemRepository.deleteAll(serviceCatalogItemRepository.findAllByBusinessIdOrderByNameAsc(businessId));
        serviceTypeRepository.deleteAll(serviceTypeRepository.findAllByBusinessIdOrderByNameAsc(businessId));
        businessRepository.deleteById(businessId);
    }

    @Test
    void backfillCreatesExactlyOneComponentAndDefaultOptionPerItemWithCorrectInvariants() {
        Business business = newBusiness("Basic");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Packages").build());

        ServiceCatalogItemResponse wash = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Wash", new BigDecimal("20.00"), false, 20, 1, false, null));
        ServiceCatalogItemResponse style = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Style", new BigDecimal("30.00"), false, 40, 1, false, null));

        ServicePackageResponse pkg = servicePackageService.create(new ServicePackageRequest(
                type.getId(), "Wash & Style", "desc", new BigDecimal("100.00"), true, 60, 1,
                List.of(new ServicePackageItemRequest(wash.id(), 1), new ServicePackageItemRequest(style.id(), 2)),
                null));

        offeringBackfillService.backfillBusiness(business.getId(), false);
        PackageContentBackfillService.PackageBackfillResult result =
                packageContentBackfillService.backfillPackage(business.getId(), pkg.id(), false);

        assertEquals(2, result.items().size());
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(business.getId(), pkg.id()).orElseThrow();
        List<PackageComponent> components = packageComponentRepository
                .findAllByBusinessIdAndOfferingIdOrderByDisplayOrderAsc(business.getId(), offering.getId());
        assertEquals(2, components.size());

        for (int i = 0; i < components.size(); i++) {
            PackageComponent c = components.get(i);
            assertTrue(c.isRequired());
            assertEquals(1, c.getMinSelections());
            assertEquals(1, c.getMaxSelections());
            assertNotNull(c.getDefaultOptionId());
            assertEquals(i, c.getDisplayOrder(), "display order must be deterministic and sequential");
            assertEquals(business.getId(), c.getBusinessId());
            assertEquals(offering.getId(), c.getOfferingId());

            var option = optionRepository.findByIdAndBusinessId(c.getDefaultOptionId(), business.getId()).orElseThrow();
            assertEquals(0, option.getPriceAdjustment().compareTo(BigDecimal.ZERO));
            assertNull(option.getOfferingId(), "backfilled option must never be linked — see class comment on double-counting");
            assertEquals(business.getId(), option.getBusinessId());
        }

        // Label reproduces legacy's own "Nx Name" display exactly — the Style item has quantity=2.
        boolean sawQuantityLabel = components.stream()
                .map(c -> optionRepository.findByIdAndBusinessId(c.getDefaultOptionId(), business.getId()).orElseThrow().getLabel())
                .anyMatch(label -> label.equals("2x Style"));
        assertTrue(sawQuantityLabel, "quantity>1 must be reflected in the label exactly like legacy includedItemLabels does");

        teardown(business.getId());
    }

    @Test
    void rerunIsIdempotentAndReconcilesRenamedCatalogItems() {
        Business business = newBusiness("Rerun");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Packages").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Blowout", new BigDecimal("25.00"), false, 30, 1, false, null));
        ServicePackageResponse pkg = servicePackageService.create(new ServicePackageRequest(
                type.getId(), "Solo Pack", null, new BigDecimal("25.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(item.id(), 1)), null));

        offeringBackfillService.backfillBusiness(business.getId(), false);
        packageContentBackfillService.backfillPackage(business.getId(), pkg.id(), false);
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(business.getId(), pkg.id()).orElseThrow();
        List<PackageComponent> firstRun = packageComponentRepository
                .findAllByBusinessIdAndOfferingIdOrderByDisplayOrderAsc(business.getId(), offering.getId());
        UUID componentId = firstRun.get(0).getId();
        UUID optionId = firstRun.get(0).getDefaultOptionId();

        // Rerun with nothing changed — must not duplicate.
        packageContentBackfillService.backfillPackage(business.getId(), pkg.id(), false);
        List<PackageComponent> secondRun = packageComponentRepository
                .findAllByBusinessIdAndOfferingIdOrderByDisplayOrderAsc(business.getId(), offering.getId());
        assertEquals(1, secondRun.size(), "rerun must not duplicate an already-backfilled component");
        assertEquals(componentId, secondRun.get(0).getId());
        assertEquals(optionId, secondRun.get(0).getDefaultOptionId());

        // Rename the underlying catalog item, rerun again — label must reconcile.
        serviceCatalogService.update(item.id(),
                new ServiceCatalogItemRequest(type.getId(), "Deluxe Blowout", new BigDecimal("25.00"), false, 30, 1, false, null));
        packageContentBackfillService.backfillPackage(business.getId(), pkg.id(), false);
        var reconciledOption = optionRepository.findByIdAndBusinessId(optionId, business.getId()).orElseThrow();
        assertEquals("Deluxe Blowout", reconciledOption.getLabel(), "a rerun must re-sync the label from the current catalog name");
        assertEquals(componentId, packageComponentRepository.findAllByBusinessIdAndOfferingIdOrderByDisplayOrderAsc(
                business.getId(), offering.getId()).get(0).getId(), "still the same component, not a duplicate");

        teardown(business.getId());
    }

    @Test
    void orphanComponentIsDetectedWhenItsSourceItemRowNoLongerExists() {
        Business business = newBusiness("Orphan");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Packages").build());
        ServiceCatalogItemResponse a = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "A", new BigDecimal("10.00"), false, 15, 1, false, null));
        ServiceCatalogItemResponse b = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "B", new BigDecimal("10.00"), false, 15, 1, false, null));
        ServicePackageResponse pkg = servicePackageService.create(new ServicePackageRequest(
                type.getId(), "AB Pack", null, new BigDecimal("20.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(a.id(), 1), new ServicePackageItemRequest(b.id(), 1)), null));

        offeringBackfillService.backfillBusiness(business.getId(), false);
        packageContentBackfillService.backfillPackage(business.getId(), pkg.id(), false);
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(business.getId(), pkg.id()).orElseThrow();

        assertTrue(packageContentBackfillService.findOrphanComponents(business.getId(), offering.getId()).isEmpty());

        // Directly deletes just item B's row (bypassing ServicePackageService.update entirely) to
        // isolate the exact invariant: a component whose OWN source item row no longer exists is
        // an orphan. Item A's row/id is left completely untouched, so A's component must NOT be
        // reported.
        List<ServicePackageItem> items = servicePackageItemRepository.findAllByPackageId(pkg.id());
        UUID itemBRowId = items.stream().filter(i -> i.getServiceCatalogId().equals(b.id())).findFirst().orElseThrow().getId();
        servicePackageItemRepository.deleteById(itemBRowId);

        List<PackageComponent> orphans = packageContentBackfillService.findOrphanComponents(business.getId(), offering.getId());
        assertEquals(1, orphans.size(), "only the component backfilled for the now-removed item B row must be flagged as an orphan");

        teardown(business.getId());
    }

    // A genuine, fresh-source finding, not a design assumption: ServicePackageService.update()
    // unconditionally deletes and re-inserts EVERY ServicePackageItem row for a package on ANY
    // edit (full-replacement semantics — confirmed by its own DTO doc and re-confirmed here by
    // its real runtime behaviour), even when the edited item set is identical in content. Every
    // row therefore gets a brand-new id on every single package edit, which means EVERY
    // previously-backfilled component for that package becomes an orphan after ANY edit, not only
    // ones whose specific item changed. This is exactly what the orphan-detection/CONTENTS_MISMATCH/
    // block-cutover mechanism exists to catch safely (Revision 4 §3/§6) — verified here so it is a
    // proven, understood consequence rather than a silent surprise. See the Phase 5C completion
    // report's own "known limitations" section for the operational implication (a package
    // backfill rerun is required after every legacy package edit, and nothing today triggers that
    // automatically).
    @Test
    void everyLegacyPackageEditOrphansAllPreviouslyBackfilledComponentsForThatPackage() {
        Business business = newBusiness("EditOrphan");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Packages").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Edit Item", new BigDecimal("10.00"), false, 15, 1, false, null));
        ServicePackageResponse pkg = servicePackageService.create(new ServicePackageRequest(
                type.getId(), "Edit Pack", null, new BigDecimal("10.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(item.id(), 1)), null));

        offeringBackfillService.backfillBusiness(business.getId(), false);
        packageContentBackfillService.backfillPackage(business.getId(), pkg.id(), false);
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(business.getId(), pkg.id()).orElseThrow();
        assertTrue(packageContentBackfillService.findOrphanComponents(business.getId(), offering.getId()).isEmpty());

        // An edit that doesn't even touch the item list's CONTENT (same single item, same
        // quantity) — only the package's own price changes.
        servicePackageService.update(pkg.id(), new ServicePackageRequest(
                type.getId(), "Edit Pack", null, new BigDecimal("15.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(item.id(), 1)), null));

        List<PackageComponent> orphans = packageContentBackfillService.findOrphanComponents(business.getId(), offering.getId());
        assertEquals(1, orphans.size(), "the item row was replaced wholesale (new id) even though its content is unchanged — confirmed real behaviour");

        // Rerunning the backfill reconciles by creating a fresh component for the new item row —
        // never auto-deletes the orphan (Revision 4 §3/§6) — so a business's operator must run it
        // again and re-verify after any package edit for that package to leave CANONICAL_DATA_INVALID.
        packageContentBackfillService.backfillPackage(business.getId(), pkg.id(), false);
        List<PackageComponent> afterRebackfill = packageComponentRepository.findAllByBusinessIdAndOfferingId(business.getId(), offering.getId());
        assertEquals(2, afterRebackfill.size(), "old orphan remains (never auto-deleted) alongside the newly-reconciled component");

        teardown(business.getId());
    }

    @Test
    void crossTenantServiceCatalogReferenceIsRejectedNotSilentlyBackfilled() {
        Business business = newBusiness("Tenant");
        Business otherBusiness = newBusiness("Other");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Packages").build());
        ServiceCatalogItemResponse legitItem = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Legit", new BigDecimal("10.00"), false, 15, 1, false, null));
        ServicePackageResponse pkg = servicePackageService.create(new ServicePackageRequest(
                type.getId(), "Tenant Pack", null, new BigDecimal("10.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(legitItem.id(), 1)), null));
        offeringBackfillService.backfillBusiness(business.getId(), false);

        TenantContext.setBusinessId(otherBusiness.getId());
        ServiceType otherType = serviceTypeRepository.save(ServiceType.builder().businessId(otherBusiness.getId()).name("Other Type").build());
        ServiceCatalogItemResponse otherItem = serviceCatalogService.create(
                new ServiceCatalogItemRequest(otherType.getId(), "Foreign", new BigDecimal("10.00"), false, 15, 1, false, null));
        TenantContext.clear();

        // Bypasses ServicePackageService's own tenant-scoped saveItems() lookup deliberately, to
        // simulate the "future legacy-layer regression" the backfill's defense-in-depth re-check
        // (Revision 4 §3) exists for — this should be structurally impossible via the real API.
        servicePackageItemRepository.save(ServicePackageItem.builder()
                .packageId(pkg.id()).serviceCatalogId(otherItem.id()).quantity(1).build());

        ApiException ex = assertThrows(ApiException.class,
                () -> packageContentBackfillService.backfillPackage(business.getId(), pkg.id(), false));
        assertTrue(ex.getMessage().contains("outside this business"));

        teardown(business.getId());
        teardown(otherBusiness.getId());
    }

    @Test
    void dryRunNeverWrites() {
        Business business = newBusiness("DryRun");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Packages").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "DryRunItem", new BigDecimal("10.00"), false, 15, 1, false, null));
        ServicePackageResponse pkg = servicePackageService.create(new ServicePackageRequest(
                type.getId(), "Dry Run Pack", null, new BigDecimal("10.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(item.id(), 1)), null));
        offeringBackfillService.backfillBusiness(business.getId(), false);
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(business.getId(), pkg.id()).orElseThrow();

        PackageContentBackfillService.PackageBackfillResult result =
                packageContentBackfillService.backfillPackage(business.getId(), pkg.id(), true);
        assertTrue(result.dryRun());
        assertEquals(1, result.items().size());
        assertTrue(packageComponentRepository.findAllByBusinessIdAndOfferingId(business.getId(), offering.getId()).isEmpty(),
                "dryRun must never write a component/option row");

        teardown(business.getId());
    }
}
