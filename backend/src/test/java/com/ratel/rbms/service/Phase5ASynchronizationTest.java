package com.ratel.rbms.service;

import com.ratel.rbms.dto.*;
import com.ratel.rbms.entity.*;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.*;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5A — proves the real ServiceCatalogService/ServicePackageService
 * mutation paths (§5 of the approved specification) actually keep their linked Offering/
 * OfferingBookingConfig in sync, atomically, through real PostgreSQL. The straightforward
 * create/update/setActive cases use @Transactional test rollback (nothing here needs to observe
 * post-rollback state). The forced-failure test deliberately does NOT — see its own comment.
 */
@SpringBootTest
class Phase5ASynchronizationTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private ServicePackageRepository servicePackageRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private OfferingBookingConfigRepository offeringBookingConfigRepository;
    @Autowired private ServiceCatalogService serviceCatalogService;
    @Autowired private ServicePackageService servicePackageService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private Business newBusiness() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return businessRepository.save(Business.builder()
                .name("Phase 5A Sync Test Business " + unique).slug("phase5a-sync-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
    }

    private ServiceType newServiceType(UUID businessId) {
        return serviceTypeRepository.save(ServiceType.builder().businessId(businessId).name("Haircut").build());
    }

    // ==================== ServiceCatalogService.create/update/setActive ====================

    @Test
    @Transactional
    void serviceCatalogCreate_createsLinkedOfferingAndConfigAtomically() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        TenantContext.setBusinessId(business.getId());

        ServiceCatalogItemRequest req = new ServiceCatalogItemRequest(type.getId(), "Deluxe Cut",
                new BigDecimal("75.00"), true, 45, 2, true, "DEPOSIT");

        ServiceCatalogItemResponse response = serviceCatalogService.create(req);

        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), response.id()).orElseThrow();
        assertEquals("Deluxe Cut", offering.getName());
        assertEquals(0, new BigDecimal("75.00").compareTo(offering.getBasePrice()));
        assertTrue(offering.isActive());

        OfferingBookingConfig config = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offering.getId(), business.getId()).orElseThrow();
        assertTrue(config.isBookableOnline());
        assertEquals(45, config.getDurationMinutes());
        assertEquals(2, config.getMaxConcurrentBookings());
        assertTrue(config.isRequiresLocation());
        assertEquals("DEPOSIT", config.getPaymentPolicyOverride());
    }

    @Test
    @Transactional
    void serviceCatalogUpdate_priceAndNameChangesPropagateToCanonicalOffering() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        TenantContext.setBusinessId(business.getId());

        ServiceCatalogItemResponse created = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Basic Cut", new BigDecimal("40.00"), false, 30, 1, false, null));

        serviceCatalogService.update(created.id(),
                new ServiceCatalogItemRequest(type.getId(), "Basic Cut (Updated)", new BigDecimal("55.00"), true, 40, 3, true, "FULL"));

        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), created.id()).orElseThrow();
        assertEquals("Basic Cut (Updated)", offering.getName());
        assertEquals(0, new BigDecimal("55.00").compareTo(offering.getBasePrice()));

        OfferingBookingConfig config = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offering.getId(), business.getId()).orElseThrow();
        assertTrue(config.isBookableOnline());
        assertEquals(40, config.getDurationMinutes());
        assertEquals(3, config.getMaxConcurrentBookings());
        assertTrue(config.isRequiresLocation());
        assertEquals("FULL", config.getPaymentPolicyOverride());
    }

    @Test
    @Transactional
    void serviceCatalogSetActive_propagatesToCanonicalOffering() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        TenantContext.setBusinessId(business.getId());

        ServiceCatalogItemResponse created = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Trial Cut", new BigDecimal("30.00"), false, 30, 1, false, null));
        Offering afterCreate = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), created.id()).orElseThrow();
        assertTrue(afterCreate.isActive());

        serviceCatalogService.setActive(created.id(), false);

        Offering afterArchive = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), created.id()).orElseThrow();
        assertFalse(afterArchive.isActive());
    }

    // ==================== ServicePackageService.create/update/setActive ====================

    @Test
    @Transactional
    void servicePackageCreate_createsLinkedOfferingAndConfigAtomically() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        TenantContext.setBusinessId(business.getId());
        ServiceCatalogItem catalogItem = serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(business.getId()).serviceTypeId(type.getId()).name("Wash").price(new BigDecimal("10.00")).build());

        ServicePackageRequest req = new ServicePackageRequest(type.getId(), "Bridal Bundle", "desc",
                new BigDecimal("400.00"), true, 90, 1,
                List.of(new ServicePackageItemRequest(catalogItem.getId(), 1)), "NONE");

        ServicePackageResponse response = servicePackageService.create(req);

        Offering offering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(business.getId(), response.id()).orElseThrow();
        assertEquals("Bridal Bundle", offering.getName());
        assertEquals(0, new BigDecimal("400.00").compareTo(offering.getBasePrice()));

        OfferingBookingConfig config = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offering.getId(), business.getId()).orElseThrow();
        assertTrue(config.isBookableOnline());
        assertEquals(90, config.getDurationMinutes());
        assertFalse(config.isRequiresLocation(), "sync must default requiresLocation to false for packages — no legacy source field exists");
    }

    @Test
    @Transactional
    void servicePackageUpdate_priceChangePropagatesToCanonicalOffering() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        TenantContext.setBusinessId(business.getId());
        ServiceCatalogItem catalogItem = serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(business.getId()).serviceTypeId(type.getId()).name("Wash").price(new BigDecimal("10.00")).build());

        ServicePackageResponse created = servicePackageService.create(new ServicePackageRequest(type.getId(), "Package A", null,
                new BigDecimal("200.00"), false, 60, 1, List.of(new ServicePackageItemRequest(catalogItem.getId(), 1)), null));

        servicePackageService.update(created.id(), new ServicePackageRequest(type.getId(), "Package A", null,
                new BigDecimal("250.00"), false, 60, 1, List.of(new ServicePackageItemRequest(catalogItem.getId(), 2)), null));

        Offering offering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(business.getId(), created.id()).orElseThrow();
        assertEquals(0, new BigDecimal("250.00").compareTo(offering.getBasePrice()));
    }

    @Test
    @Transactional
    void servicePackageSetActive_propagatesToCanonicalOffering() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        TenantContext.setBusinessId(business.getId());
        ServiceCatalogItem catalogItem = serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(business.getId()).serviceTypeId(type.getId()).name("Wash").price(new BigDecimal("10.00")).build());

        ServicePackageResponse created = servicePackageService.create(new ServicePackageRequest(type.getId(), "Package B", null,
                new BigDecimal("200.00"), false, 60, 1, List.of(new ServicePackageItemRequest(catalogItem.getId(), 1)), null));

        servicePackageService.setActive(created.id(), false);

        Offering offering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(business.getId(), created.id()).orElseThrow();
        assertFalse(offering.isActive());
    }

    // ==================== Forced failure — real rollback proof ====================

    @Test
    void forcedFailureAfterCanonicalSyncRollsBackBothLegacyAndCanonicalWrites() {
        // Deliberately NOT @Transactional at the test level — same reasoning as
        // Phase4RollbackTest: a JUnit-wrapping transaction would only ever flag itself
        // rollback-only without a real rollback happening before this test's own reads run,
        // which would prove nothing. servicePackageService.create() owns its own real,
        // top-level transaction here; when it throws, Spring performs a genuine rollback and
        // closes it before control returns to this test.
        Business business = businessRepository.save(Business.builder()
                .name("Phase 5A Rollback Test Business " + UUID.randomUUID().toString().substring(0, 8))
                .slug("phase5a-rollback-" + UUID.randomUUID().toString().substring(0, 8))
                .industry(Industry.OTHER).currency("GHS").build());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Haircut").build());
        TenantContext.setBusinessId(business.getId());

        // Real, unmodified application code: ServicePackageService.create() now syncs the
        // Offering/OfferingBookingConfig immediately after saving the ServicePackage row, BEFORE
        // saveItems() — which throws a genuine ApiException(BAD_REQUEST) when a line item
        // references a ServiceCatalogItem id that doesn't exist. This is the real validation
        // check already in saveItems(), not a test-only hook.
        UUID bogusServiceCatalogId = UUID.randomUUID();
        ServicePackageRequest req = new ServicePackageRequest(type.getId(), "Doomed Package", null,
                new BigDecimal("300.00"), false, 60, 1,
                List.of(new ServicePackageItemRequest(bogusServiceCatalogId, 1)), null);

        ApiException ex = assertThrows(ApiException.class, () -> servicePackageService.create(req));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());

        // ---- Fresh reads, after the real rollback, each its own new Spring Data transaction ----

        List<ServicePackage> packagesAfter = servicePackageRepository.findAllByBusinessIdOrderByNameAsc(business.getId());
        assertTrue(packagesAfter.isEmpty(), "the ServicePackage row itself must not survive — it was flushed before the failure, then rolled back");

        List<Offering> offeringsAfter = offeringRepository.findAll().stream()
                .filter(o -> business.getId().equals(o.getBusinessId()))
                .toList();
        assertTrue(offeringsAfter.isEmpty(), "no canonical Offering may survive a rolled-back legacy write, even though the sync itself succeeded before the failure");

        // Nothing here is append-only/undeletable — clean up the fixture itself too.
        serviceTypeRepository.deleteById(type.getId());
        businessRepository.deleteById(business.getId());
    }
}
