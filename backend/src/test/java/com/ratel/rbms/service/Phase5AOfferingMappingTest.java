package com.ratel.rbms.service;

import com.ratel.rbms.entity.*;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5A — proves the canonical Offering model schema (V57) against
 * real PostgreSQL: legacy mapping uniqueness/mutual-exclusivity, tenant isolation on every new
 * composite FK, OfferingBookingConfig constraints, and the Option→Offering SERVICE-type rule
 * (service-layer, per the approved trigger-free design). @Transactional rolls every test back.
 */
@SpringBootTest
@Transactional
class Phase5AOfferingMappingTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private ServicePackageRepository servicePackageRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private OfferingBookingConfigRepository offeringBookingConfigRepository;
    @Autowired private OptionRepository optionRepository;
    @Autowired private OfferingResolutionService offeringResolutionService;
    @Autowired private OfferingSyncService offeringSyncService;
    @PersistenceContext private EntityManager entityManager;

    private Business newBusiness() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return businessRepository.save(Business.builder()
                .name("Phase 5A Test Business " + unique).slug("phase5a-test-business-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
    }

    private ServiceType newServiceType(UUID businessId) {
        return serviceTypeRepository.save(ServiceType.builder().businessId(businessId).name("Haircut").build());
    }

    private ServiceCatalogItem newCatalogItem(UUID businessId, UUID serviceTypeId) {
        return serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(businessId).serviceTypeId(serviceTypeId).name("Standard Cut")
                .price(new BigDecimal("50.00")).active(true).build());
    }

    private ServicePackage newPackage(UUID businessId, UUID serviceTypeId) {
        return servicePackageRepository.save(ServicePackage.builder()
                .businessId(businessId).serviceTypeId(serviceTypeId).name("Bridal Package")
                .price(new BigDecimal("500.00")).active(true).build());
    }

    // ==================== Mapping ====================

    @Test
    void serviceCatalogItemMapsToExactlyOneServiceOffering() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newCatalogItem(business.getId(), type.getId());

        offeringSyncService.syncServiceCatalogItem(item);

        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.getId()).orElseThrow();
        assertEquals("SERVICE", offering.getType());
        assertEquals(item.getName(), offering.getName());
        assertEquals(0, item.getPrice().compareTo(offering.getBasePrice()));
        assertTrue(offering.isActive());
        assertNull(offering.getLegacyServicePackageId());
    }

    @Test
    void servicePackageMapsToExactlyOnePackageOffering() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServicePackage pkg = newPackage(business.getId(), type.getId());

        offeringSyncService.syncServicePackage(pkg);

        Offering offering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(business.getId(), pkg.getId()).orElseThrow();
        assertEquals("PACKAGE", offering.getType());
        assertEquals(pkg.getName(), offering.getName());
        assertNull(offering.getLegacyServiceCatalogId());
    }

    @Test
    void duplicateMappingToTheSameServiceCatalogItemIsRejectedByTheDatabase() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newCatalogItem(business.getId(), type.getId());
        offeringSyncService.syncServiceCatalogItem(item); // first mapping

        Offering duplicate = Offering.builder()
                .businessId(business.getId()).type("SERVICE").name("Duplicate")
                .legacyServiceCatalogId(item.getId()).build();

        assertThrows(DataIntegrityViolationException.class,
                () -> offeringRepository.saveAndFlush(duplicate),
                "uq_offerings_legacy_service_catalog_id must reject a second Offering mapped to the same legacy item");
    }

    @Test
    void bothLegacyMappingColumnsSetIsRejectedByTheDatabase() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newCatalogItem(business.getId(), type.getId());
        ServicePackage pkg = newPackage(business.getId(), type.getId());

        Offering ambiguous = Offering.builder()
                .businessId(business.getId()).type("SERVICE").name("Ambiguous")
                .legacyServiceCatalogId(item.getId()).legacyServicePackageId(pkg.getId()).build();

        assertThrows(DataIntegrityViolationException.class,
                () -> offeringRepository.saveAndFlush(ambiguous),
                "chk_offerings_legacy_mutually_exclusive must reject both mapping columns set");
    }

    @Test
    void mappingToAnotherBusinesssLegacyItemIsRejectedByTheDatabase() {
        Business a = newBusiness();
        Business b = newBusiness();
        ServiceType typeB = newServiceType(b.getId());
        ServiceCatalogItem itemB = newCatalogItem(b.getId(), typeB.getId());

        Offering crossBusiness = Offering.builder()
                .businessId(a.getId()) // wrong business for itemB
                .type("SERVICE").name("Cross business")
                .legacyServiceCatalogId(itemB.getId()).build();

        assertThrows(DataIntegrityViolationException.class,
                () -> offeringRepository.saveAndFlush(crossBusiness),
                "fk_offerings_legacy_service_catalog must reject a mismatched (business_id, legacy_service_catalog_id) pair");
    }

    @Test
    void mappingToAnotherBusinesssPackageIsRejectedByTheDatabase() {
        Business a = newBusiness();
        Business b = newBusiness();
        ServiceType typeB = newServiceType(b.getId());
        ServicePackage pkgB = newPackage(b.getId(), typeB.getId());

        Offering crossBusiness = Offering.builder()
                .businessId(a.getId())
                .type("PACKAGE").name("Cross business")
                .legacyServicePackageId(pkgB.getId()).build();

        assertThrows(DataIntegrityViolationException.class,
                () -> offeringRepository.saveAndFlush(crossBusiness),
                "fk_offerings_legacy_service_package must reject a mismatched (business_id, legacy_service_package_id) pair");
    }

    @Test
    void unmappedActiveRecordIsDetectableThroughResolution() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newCatalogItem(business.getId(), type.getId());
        // Deliberately never synced.

        UUID resolved = offeringResolutionService.resolveOfferingId(
                business.getId(), OfferingResolutionService.LegacyType.SERVICE_CATALOG_ITEM, item.getId());
        assertNull(resolved, "an unmapped legacy item must resolve to null, never guess or fabricate an Offering");
    }

    @Test
    void resolutionRequiresAllThreeArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> offeringResolutionService.resolveOfferingId(null, OfferingResolutionService.LegacyType.SERVICE_CATALOG_ITEM, UUID.randomUUID()));
    }

    // ==================== Booking config ====================

    @Test
    void offeringBookingConfigIsOneToOne() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newCatalogItem(business.getId(), type.getId());
        offeringSyncService.syncServiceCatalogItem(item);
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.getId()).orElseThrow();

        // offeringId is a manually-assigned @Id (no @GeneratedValue) — repository.save() on a
        // duplicate id would silently perform a Hibernate merge (UPDATE), never exercising the PK
        // constraint at all. entityManager.persist() always attempts a genuine INSERT — but the
        // sync call above already left the real row tracked in THIS session's own persistence
        // context, so persist()-ing a second, different Java object with the same id would first
        // be caught by Hibernate's own in-session identity check (NonUniqueObjectException), not
        // the database. Clearing the persistence context first forces a genuine round-trip to
        // Postgres, actually exercising the real SQL-level PRIMARY KEY constraint.
        entityManager.clear();
        OfferingBookingConfig duplicate = OfferingBookingConfig.builder()
                .offeringId(offering.getId()).businessId(business.getId()).build();

        // Raw EntityManager calls bypass Spring's repository-proxy exception translation, so the
        // real, un-translated Hibernate exception surfaces here — still the genuine SQL-level
        // PRIMARY KEY violation (see the real Postgres error this produces: "duplicate key value
        // violates unique constraint offering_booking_config_pkey").
        assertThrows(org.hibernate.exception.ConstraintViolationException.class,
                () -> { entityManager.persist(duplicate); entityManager.flush(); },
                "offering_booking_config's PK must reject a second row for the same offering");
    }

    @Test
    void offeringBookingConfigCrossBusinessReferenceIsRejected() {
        Business a = newBusiness();
        Business b = newBusiness();
        ServiceType typeB = newServiceType(b.getId());
        ServiceCatalogItem itemB = newCatalogItem(b.getId(), typeB.getId());
        offeringSyncService.syncServiceCatalogItem(itemB);
        Offering offeringB = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(b.getId(), itemB.getId()).orElseThrow();

        OfferingBookingConfig crossBusiness = OfferingBookingConfig.builder()
                .offeringId(offeringB.getId()).businessId(a.getId()).build(); // wrong business for offeringB

        assertThrows(DataIntegrityViolationException.class,
                () -> offeringBookingConfigRepository.saveAndFlush(crossBusiness),
                "fk_offering_booking_config_offering must reject a mismatched (business_id, offering_id) pair");
    }

    @Test
    void offeringBookingConfigRejectsInvalidDuration() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newCatalogItem(business.getId(), type.getId());
        offeringSyncService.syncServiceCatalogItem(item);
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.getId()).orElseThrow();

        // Bypass the service layer (which always sends a positive duration) to prove the DB CHECK.
        OfferingBookingConfig invalid = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offering.getId(), business.getId()).orElseThrow();
        invalid.setDurationMinutes(0);

        assertThrows(DataIntegrityViolationException.class,
                () -> offeringBookingConfigRepository.saveAndFlush(invalid),
                "chk_offering_booking_config_duration_positive must reject duration_minutes <= 0");
    }

    @Test
    void offeringBookingConfigRejectsInvalidMaxConcurrency() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newCatalogItem(business.getId(), type.getId());
        offeringSyncService.syncServiceCatalogItem(item);
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.getId()).orElseThrow();

        OfferingBookingConfig invalid = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offering.getId(), business.getId()).orElseThrow();
        invalid.setMaxConcurrentBookings(0);

        assertThrows(DataIntegrityViolationException.class,
                () -> offeringBookingConfigRepository.saveAndFlush(invalid),
                "chk_offering_booking_config_capacity_positive must reject max_concurrent_bookings <= 0");
    }

    @Test
    void offeringBookingConfigRejectsInvalidPaymentPolicy() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newCatalogItem(business.getId(), type.getId());
        offeringSyncService.syncServiceCatalogItem(item);
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.getId()).orElseThrow();

        OfferingBookingConfig invalid = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offering.getId(), business.getId()).orElseThrow();
        invalid.setPaymentPolicyOverride("BOGUS");

        assertThrows(DataIntegrityViolationException.class,
                () -> offeringBookingConfigRepository.saveAndFlush(invalid),
                "chk_offering_booking_config_payment_policy must reject an unrecognized value");
    }

    @Test
    void packageOfferingBookingConfigCanCarryRequiresLocationTrue() {
        // Proves the legacy asymmetry (ServicePackage has no requiresLocation column) is closed
        // at the canonical level — a PACKAGE Offering's config can legitimately be set to true,
        // even though the Phase 5A backfill/sync itself always defaults new ones to false.
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServicePackage pkg = newPackage(business.getId(), type.getId());
        offeringSyncService.syncServicePackage(pkg);
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(business.getId(), pkg.getId()).orElseThrow();

        OfferingBookingConfig config = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offering.getId(), business.getId()).orElseThrow();
        assertFalse(config.isRequiresLocation(), "sync must default requiresLocation to false for packages, preserving current behaviour");

        config.setRequiresLocation(true);
        offeringBookingConfigRepository.saveAndFlush(config);
        OfferingBookingConfig reloaded = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offering.getId(), business.getId()).orElseThrow();
        assertTrue(reloaded.isRequiresLocation());
    }

    // ==================== Option → Offering ====================

    @Test
    void optionCanReferenceAServiceOffering() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newCatalogItem(business.getId(), type.getId());
        offeringSyncService.syncServiceCatalogItem(item);
        Offering serviceOffering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.getId()).orElseThrow();

        offeringResolutionService.requireServiceType(serviceOffering); // must not throw

        Option option = optionRepository.save(Option.builder()
                .businessId(business.getId()).offeringId(serviceOffering.getId())
                .label("Standard Cut").priceAdjustment(BigDecimal.ZERO).build());
        Option reloaded = optionRepository.findById(option.getId()).orElseThrow();
        assertEquals(serviceOffering.getId(), reloaded.getOfferingId());
    }

    @Test
    void optionReferencingANonServiceOfferingIsRejectedAtTheServiceLevel() {
        Business business = newBusiness();
        ServiceType type = newServiceType(business.getId());
        ServicePackage pkg = newPackage(business.getId(), type.getId());
        offeringSyncService.syncServicePackage(pkg);
        Offering packageOffering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(business.getId(), pkg.getId()).orElseThrow();

        ApiException ex = assertThrows(ApiException.class, () -> offeringResolutionService.requireServiceType(packageOffering));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        // The DB itself has no type-check on options.offering_id (deliberately trigger-free) —
        // this proves the rule is real at the service layer, which is where it's actually enforced.
    }

    @Test
    void optionCrossBusinessOfferingReferenceIsRejectedByTheDatabase() {
        Business a = newBusiness();
        Business b = newBusiness();
        ServiceType typeB = newServiceType(b.getId());
        ServiceCatalogItem itemB = newCatalogItem(b.getId(), typeB.getId());
        offeringSyncService.syncServiceCatalogItem(itemB);
        Offering offeringB = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(b.getId(), itemB.getId()).orElseThrow();

        Option crossBusiness = Option.builder()
                .businessId(a.getId()) // wrong business for offeringB
                .offeringId(offeringB.getId()).label("Cross business").build();

        assertThrows(DataIntegrityViolationException.class,
                () -> optionRepository.saveAndFlush(crossBusiness),
                "fk_options_offering must reject a mismatched (business_id, offering_id) pair");
    }

    @Test
    void optionWithNullOfferingIdPreservesStandaloneModifierCapability() {
        Business business = newBusiness();
        Option standalone = optionRepository.save(Option.builder()
                .businessId(business.getId()).label("Extra Long Hair").priceAdjustment(new BigDecimal("20.00")).build());
        Option reloaded = optionRepository.findById(standalone.getId()).orElseThrow();
        assertNull(reloaded.getOfferingId());
        assertEquals("Extra Long Hair", reloaded.getLabel());
    }
}
