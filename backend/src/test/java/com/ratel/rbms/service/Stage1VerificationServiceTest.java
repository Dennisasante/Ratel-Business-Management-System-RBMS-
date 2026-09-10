package com.ratel.rbms.service;

import com.ratel.rbms.dto.ServiceCatalogItemRequest;
import com.ratel.rbms.dto.ServiceCatalogItemResponse;
import com.ratel.rbms.dto.ServicePackageItemRequest;
import com.ratel.rbms.dto.ServicePackageRequest;
import com.ratel.rbms.dto.ServicePackageResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.Policy;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.OfferingBookingConfigRepository;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.OptionRepository;
import com.ratel.rbms.repository.PackageComponentRepository;
import com.ratel.rbms.repository.PolicyRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
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
 * Talia Unified Platform, Phase 5C — real-PostgreSQL proof of the Stage 1 verification contract
 * (frozen design, Revision 4 §1/§3, tightened §19/§20): the nine commercial-parity dimensions
 * detect real, deliberately-introduced mismatches with the correct taxonomy codes, and policy
 * widening/narrowing are evaluated separately from commercial parity.
 */
@SpringBootTest
class Stage1VerificationServiceTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private ServicePackageRepository servicePackageRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private OfferingBookingConfigRepository offeringBookingConfigRepository;
    @Autowired private PackageComponentRepository packageComponentRepository;
    @Autowired private OptionRepository optionRepository;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private ServiceCatalogService serviceCatalogService;
    @Autowired private ServicePackageService servicePackageService;
    @Autowired private OfferingBackfillService offeringBackfillService;
    @Autowired private PackageContentBackfillService packageContentBackfillService;
    @Autowired private Stage1VerificationService stage1VerificationService;

    private Business business;

    private Business newBusiness(String label) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return businessRepository.save(Business.builder()
                .name("Phase5C Verify " + label + " " + unique)
                .slug("phase5c-verify-" + label.toLowerCase() + "-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private void teardown(UUID businessId) {
        policyRepository.deleteAll(policyRepository.findAll().stream().filter(p -> p.getBusinessId().equals(businessId)).toList());
        offeringBookingConfigRepository.findAll().stream()
                .filter(c -> c.getBusinessId().equals(businessId))
                .forEach(c -> offeringBookingConfigRepository.deleteById(c.getOfferingId()));
        packageComponentRepository.deleteAll(packageComponentRepository.findAll().stream().filter(c -> c.getBusinessId().equals(businessId)).toList());
        offeringRepository.deleteAll(offeringRepository.findAll().stream().filter(o -> o.getBusinessId().equals(businessId)).toList());
        servicePackageRepository.deleteAll(servicePackageRepository.findAllByBusinessIdOrderByNameAsc(businessId));
        serviceCatalogItemRepository.deleteAll(serviceCatalogItemRepository.findAllByBusinessIdOrderByNameAsc(businessId));
        serviceTypeRepository.deleteAll(serviceTypeRepository.findAllByBusinessIdOrderByNameAsc(businessId));
        businessRepository.deleteById(businessId);
    }

    @Test
    void cleanBusinessProducesZeroMismatches() {
        business = newBusiness("Clean");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Clean Service", new BigDecimal("40.00"), true, 30, 1, false, null));
        ServicePackageResponse pkg = servicePackageService.create(new ServicePackageRequest(
                type.getId(), "Clean Package", null, new BigDecimal("40.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(item.id(), 1)), null));

        offeringBackfillService.backfillBusiness(business.getId(), false);
        packageContentBackfillService.backfillPackage(business.getId(), pkg.id(), false);

        VerificationResult result = stage1VerificationService.verifyBusiness(business.getId());
        assertTrue(result.clean(), "mismatches=" + result.mismatches());

        teardown(business.getId());
    }

    @Test
    void priceMismatchIsDetected() {
        business = newBusiness("Price");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Price Drift", new BigDecimal("40.00"), true, 30, 1, false, null));
        offeringBackfillService.backfillBusiness(business.getId(), false);

        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.id()).orElseThrow();
        offering.setBasePrice(new BigDecimal("999.00")); // simulate canonical drift, bypassing the sync path
        offeringRepository.save(offering);

        VerificationResult result = stage1VerificationService.verifyBusiness(business.getId());
        assertFalse(result.clean());
        assertTrue(result.mismatches().stream().anyMatch(m -> m.code().equals(ParityMismatch.PRICE_MISMATCH)));

        teardown(business.getId());
    }

    @Test
    void labelAndDurationAndAvailabilityMismatchesAreDetected() {
        business = newBusiness("Multi");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Multi Drift", new BigDecimal("40.00"), true, 30, 1, false, null));
        offeringBackfillService.backfillBusiness(business.getId(), false);

        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.id()).orElseThrow();
        offering.setName("Wrong Name");
        offeringRepository.save(offering);
        var config = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offering.getId(), business.getId()).orElseThrow();
        config.setDurationMinutes(999);
        config.setBookableOnline(false); // legacy is bookableOnline=true — availability mismatch
        offeringBookingConfigRepository.save(config);

        VerificationResult result = stage1VerificationService.verifyBusiness(business.getId());
        assertFalse(result.clean());
        List<String> codes = result.mismatches().stream().map(ParityMismatch::code).toList();
        assertTrue(codes.contains(ParityMismatch.LABEL_MISMATCH));
        assertTrue(codes.contains(ParityMismatch.DURATION_MISMATCH));
        assertTrue(codes.contains(ParityMismatch.AVAILABILITY_FLAG_MISMATCH));

        teardown(business.getId());
    }

    @Test
    void missingOfferingMappingIsReportedAsAvailabilityMismatchAndBlocks() {
        business = newBusiness("Missing");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        // Bypasses ServiceCatalogService.create() deliberately — that method's own
        // offeringSyncService.syncServiceCatalogItem() call means every item created through the
        // real API is mapped immediately; the only way "no mapping exists" occurs in reality is
        // pre-Phase-5A legacy data that has never gone through OfferingBackfillService, so this
        // simulates that state directly via the repository.
        var item = serviceCatalogItemRepository.save(com.ratel.rbms.entity.ServiceCatalogItem.builder()
                .businessId(business.getId()).serviceTypeId(type.getId()).name("Unmapped")
                .price(new BigDecimal("40.00")).active(true).bookableOnline(true).durationMinutes(30).maxConcurrentBookings(1)
                .build());
        assertTrue(offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.getId()).isEmpty(),
                "precondition: genuinely no mapping exists yet");

        VerificationResult result = stage1VerificationService.verifyBusiness(business.getId());
        assertFalse(result.clean());
        assertTrue(result.mismatches().stream().anyMatch(m -> m.code().equals(ParityMismatch.AVAILABILITY_FLAG_MISMATCH)
                && m.detail().contains("No canonical Offering mapping")));

        teardown(business.getId());
    }

    @Test
    void packageContentsMismatchIsDetectedWhenBackfillHasNotRun() {
        business = newBusiness("Contents");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Pkg Item", new BigDecimal("40.00"), false, 30, 1, false, null));
        ServicePackageResponse pkg = servicePackageService.create(new ServicePackageRequest(
                type.getId(), "Unbackfilled Pack", null, new BigDecimal("40.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(item.id(), 1)), null));
        offeringBackfillService.backfillBusiness(business.getId(), false);
        // Deliberately never runs PackageContentBackfillService — zero PackageComponent rows exist,
        // exactly the Phase 5A-only state this test isolates.

        VerificationResult result = stage1VerificationService.verifyBusiness(business.getId());
        assertFalse(result.clean());
        assertTrue(result.mismatches().stream().anyMatch(m -> m.code().equals(ParityMismatch.CONTENTS_MISMATCH)),
                "an un-backfilled package must fail dimension 8, not merely pass on price coincidence");

        teardown(business.getId());
    }

    @Test
    void policyWideningIsInformationalAndDoesNotBlockCleanliness() {
        business = newBusiness("Widen");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Policy Widen", new BigDecimal("40.00"), true, 30, 1, false, null));
        offeringBackfillService.backfillBusiness(business.getId(), false);
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.id()).orElseThrow();

        // An offering-scoped policy — legacy's offering-blind matching (offeringId=null) can never
        // see it (PolicyRepository.findApplicable's own query shape, re-confirmed by fresh source:
        // "p.offeringId IS NULL OR p.offeringId = :offeringId" — a null :offeringId only ever
        // matches the business-wide half), canonical's offering-aware matching does.
        Policy scoped = policyRepository.save(Policy.builder()
                .businessId(business.getId()).policyKey("widen-test-" + UUID.randomUUID())
                .appliesToAction("BOOKING_CREATE").offeringId(offering.getId()).active(true)
                .createdBy(UUID.randomUUID()).build());

        VerificationResult result = stage1VerificationService.verifyBusiness(business.getId());
        assertTrue(result.clean(), "policy widening must never count as a commercial-parity mismatch: " + result.mismatches());
        assertFalse(result.policyWidenings().isEmpty(), "the widening must still be reported, informationally");
        assertTrue(result.mismatches().stream().noneMatch(m -> m.code().equals(ParityMismatch.POLICY_SCOPE_NARROWED)));

        policyRepository.deleteById(scoped.getId());
        teardown(business.getId());
    }

    // Structural finding, proven rather than assumed: given PolicyRepository.findApplicable's own
    // query ("offeringId IS NULL OR offeringId = :offeringId"), canonical's (offering-aware)
    // result set is ALWAYS a superset of legacy's (offering-blind) result set for the SAME
    // action — POLICY_SCOPE_NARROWED is structurally unreachable through this code path, exactly
    // the same class of finding as Phase 5B's own SUBSTITUTION_LIMIT_EXCEEDED. The mismatch code
    // stays implemented (defensive, forward-compatible), just never observed to fire here.
    @Test
    void policyNarrowingIsStructurallyUnreachableGivenTheCurrentApplicablePoliciesQuery() {
        business = newBusiness("Narrow");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Policy Business Wide", new BigDecimal("40.00"), true, 30, 1, false, null));
        offeringBackfillService.backfillBusiness(business.getId(), false);

        Policy businessWide = policyRepository.save(Policy.builder()
                .businessId(business.getId()).policyKey("business-wide-" + UUID.randomUUID())
                .appliesToAction("BOOKING_CREATE").offeringId(null).active(true)
                .createdBy(UUID.randomUUID()).build());

        VerificationResult result = stage1VerificationService.verifyBusiness(business.getId());
        assertTrue(result.clean(), "a business-wide policy must be visible to both legacy and canonical matching equally: " + result.mismatches());
        assertTrue(result.mismatches().stream().noneMatch(m -> m.code().equals(ParityMismatch.POLICY_SCOPE_NARROWED)));

        policyRepository.deleteById(businessWide.getId());
        teardown(business.getId());
    }
}
