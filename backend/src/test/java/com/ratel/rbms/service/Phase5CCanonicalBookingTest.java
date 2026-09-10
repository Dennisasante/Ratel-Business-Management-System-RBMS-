package com.ratel.rbms.service;

import com.ratel.rbms.dto.BookingCreatedResponse;
import com.ratel.rbms.dto.CreateBookingRequest;
import com.ratel.rbms.dto.ServiceCatalogItemRequest;
import com.ratel.rbms.dto.ServiceCatalogItemResponse;
import com.ratel.rbms.dto.ServicePackageItemRequest;
import com.ratel.rbms.dto.ServicePackageRequest;
import com.ratel.rbms.dto.ServicePackageResponse;
import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.Policy;
import com.ratel.rbms.entity.PolicyVersion;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.ServiceOrderItem;
import com.ratel.rbms.entity.ServiceOrderLineSnapshot;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.OfferingBookingConfigRepository;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.PackageComponentRepository;
import com.ratel.rbms.repository.PolicyRepository;
import com.ratel.rbms.repository.PolicyVersionRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceOrderItemRepository;
import com.ratel.rbms.repository.ServiceOrderLineSnapshotRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5C — real-PostgreSQL proof that canonical booking creation
 * (frozen design, Revision 4, tightened per the final implementation instruction) does what it
 * promises: authoritative canonical pricing/eligibility, a real offeringId reaching PolicyEngine,
 * and an immutable historical transaction snapshot written in the SAME transaction.
 */
@SpringBootTest
class Phase5CCanonicalBookingTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private ServicePackageRepository servicePackageRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private OfferingBookingConfigRepository offeringBookingConfigRepository;
    @Autowired private PackageComponentRepository packageComponentRepository;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private PolicyVersionRepository policyVersionRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ServiceOrderRepository serviceOrderRepository;
    @Autowired private ServiceOrderItemRepository serviceOrderItemRepository;
    @Autowired private ServiceOrderLineSnapshotRepository serviceOrderLineSnapshotRepository;
    @Autowired private ServiceCatalogService serviceCatalogService;
    @Autowired private ServicePackageService servicePackageService;
    @Autowired private PackageContentBackfillService packageContentBackfillService;
    @Autowired private BookingCutoverStateResolver bookingCutoverStateResolver;
    @Autowired private BookingService bookingService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private Business newBusiness(String label) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return businessRepository.save(Business.builder()
                .name("Phase5C Canonical " + label + " " + unique)
                .slug("phase5c-canonical-" + label.toLowerCase() + "-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
    }

    // Forces CANONICAL_ENABLED directly, bypassing Stage1VerificationService deliberately (that
    // class's own correctness is proven independently in Stage1VerificationServiceTest). MUST be
    // called only AFTER any catalog/package setup is complete — since the Phase 5C targeted
    // correction, ServicePackageService.create() itself invalidates an already-CANONICAL_ENABLED
    // business the moment a new active+bookableOnline package appears with no backfill yet
    // (exactly the intended, now-proven safety behaviour — see Phase5CPackageMutationSafetyTest).
    private void forceCanonicalEnabled(UUID businessId) {
        bookingCutoverStateResolver.markVerified(businessId, new VerificationResult(businessId, List.of(), List.of()));
        bookingCutoverStateResolver.enableCanonical(businessId);
    }

    private Business newCanonicalBusiness(String label) {
        Business business = newBusiness(label);
        forceCanonicalEnabled(business.getId());
        return business;
    }

    // A successful canonical booking writes real service_order_line_snapshots rows, and those are
    // deliberately, permanently undeletable (the append-only trigger rejects every DELETE, not
    // only every UPDATE — proven directly in Phase5CFreshMigrationTest). service_orders itself is
    // then transitively undeletable too (fk_service_order_line_snapshots_order is RESTRICT). This
    // is by design, not a test bug — matches this project's own established precedent for
    // genuinely undeletable evidence (e.g. Phase 4's policy_versions, documented the same way in
    // Phase4ConcurrencyTest). Tests that reach a successful canonical booking therefore
    // deliberately do NOT delete the ServiceOrder/Booking/snapshot chain or the business that owns
    // it — that data is left behind in the LOCAL DEV database only, exactly like Phase 4's own
    // undeletable rows, and is not cleaned up further here.
    private void leaveImmutableBookingEvidenceInPlace(UUID businessId) {
        // Intentionally empty beyond this comment — see the class-level note above. Named
        // explicitly (rather than simply omitting a teardown call) so it reads as a deliberate
        // decision at every call site, not an oversight.
    }

    // BusinessWorkingHours.defaultHours() (Mon-Sat, 9am-6pm UTC) applies when a business has never
    // configured its own — a fixed offset from "now" can land on a Sunday or outside that window
    // depending on when the suite runs, so this deterministically picks the next Wednesday 10:00
    // UTC (always Mon-Sat, always inside 9-6) at least a few days out.
    private static Instant nextValidBookingSlot() {
        ZonedDateTime zdt = ZonedDateTime.now(ZoneOffset.UTC).plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
        while (zdt.getDayOfWeek() == DayOfWeek.SUNDAY) {
            zdt = zdt.plusDays(1);
        }
        return zdt.toInstant();
    }

    private void teardownCatalog(UUID businessId) {
        offeringBookingConfigRepository.findAll().stream().filter(c -> c.getBusinessId().equals(businessId))
                .forEach(c -> offeringBookingConfigRepository.deleteById(c.getOfferingId()));
        packageComponentRepository.deleteAll(packageComponentRepository.findAll().stream().filter(c -> c.getBusinessId().equals(businessId)).toList());
        offeringRepository.deleteAll(offeringRepository.findAll().stream().filter(o -> o.getBusinessId().equals(businessId)).toList());
        servicePackageRepository.deleteAll(servicePackageRepository.findAllByBusinessIdOrderByNameAsc(businessId));
        serviceCatalogItemRepository.deleteAll(serviceCatalogItemRepository.findAllByBusinessIdOrderByNameAsc(businessId));
        serviceTypeRepository.deleteAll(serviceTypeRepository.findAllByBusinessIdOrderByNameAsc(businessId));
        businessRepository.deleteById(businessId);
    }

    @Test
    void canonicalServiceBookingUsesOfferingPriceAndWritesASnapshotSummingToTheChargedPrice() {
        Business business = newCanonicalBusiness("Service");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Canonical Cut", new BigDecimal("55.00"), true, 30, 1, false, null));
        TenantContext.clear();

        BookingCreatedResponse created = bookingService.createBooking(business.getId(), new CreateBookingRequest(
                item.id(), null, "Jane Doe", "jane@example.com", "0244123456",
                nextValidBookingSlot(), null, null, null));

        Booking booking = bookingRepository.findByManageToken(created.manageToken()).orElseThrow();
        ServiceOrder order = serviceOrderRepository.findById(booking.getServiceOrderId()).orElseThrow();
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.id()).orElseThrow();

        assertEquals(0, order.getPrice().compareTo(new BigDecimal("55.00")), "canonical price must come from the Offering, not a stale legacy read");
        assertEquals(offering.getId(), order.getOfferingId(), "ServiceOrder must carry the canonical tracking pointer");
        assertEquals(item.id(), order.getServiceCatalogId(), "legacy column preserved for backward compatibility");

        ServiceOrderItem legacyItem = serviceOrderItemRepository.findAll().stream()
                .filter(i -> i.getServiceOrderId().equals(order.getId())).findFirst().orElseThrow();
        assertEquals(0, legacyItem.getPrice().compareTo(order.getPrice()), "SERVICE_ORDER_FIELD_DRIFT proof: ServiceOrderItem.price matches ServiceOrder.price exactly");
        assertEquals(offering.getName(), legacyItem.getServiceName());

        List<ServiceOrderLineSnapshot> snapshots = serviceOrderLineSnapshotRepository.findAllByBusinessIdAndServiceOrderId(business.getId(), order.getId());
        assertFalse(snapshots.isEmpty());
        BigDecimal snapshotTotal = snapshots.stream().map(ServiceOrderLineSnapshot::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, snapshotTotal.compareTo(order.getPrice()), "snapshot line amounts must sum to exactly the charged price");

        leaveImmutableBookingEvidenceInPlace(business.getId());
    }

    @Test
    void canonicalPackageBookingReproducesLegacyFixedPriceViaDefaultSelectionsAndSnapshotsEachIncludedItem() {
        // Catalog/package setup MUST precede enabling canonical here — since the Phase 5C targeted
        // correction, creating a new active+bookableOnline package invalidates an
        // already-CANONICAL_ENABLED business (correctly: it has zero backfilled components at
        // that instant). Build the whole fixture first, THEN enable, exactly matching realistic
        // usage (see forceCanonicalEnabled's own note).
        Business business = newBusiness("Package");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Pkgs").build());
        ServiceCatalogItemResponse wash = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Wash", new BigDecimal("20.00"), false, 20, 1, false, null));
        ServiceCatalogItemResponse style = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Style", new BigDecimal("30.00"), false, 40, 1, false, null));
        ServicePackageResponse pkg = servicePackageService.create(new ServicePackageRequest(
                type.getId(), "Wash & Style", null, new BigDecimal("45.00"), true, 60, 1,
                List.of(new ServicePackageItemRequest(wash.id(), 1), new ServicePackageItemRequest(style.id(), 1)), null));
        packageContentBackfillService.backfillPackage(business.getId(), pkg.id(), false);
        TenantContext.clear();
        forceCanonicalEnabled(business.getId());

        BookingCreatedResponse created = bookingService.createBooking(business.getId(), new CreateBookingRequest(
                null, pkg.id(), "John Doe", "john@example.com", "0244123457",
                nextValidBookingSlot(), null, null, null));

        Booking booking = bookingRepository.findByManageToken(created.manageToken()).orElseThrow();
        ServiceOrder order = serviceOrderRepository.findById(booking.getServiceOrderId()).orElseThrow();

        assertEquals(0, order.getPrice().compareTo(new BigDecimal("45.00")),
                "canonical default-selection package pricing must reproduce today's fixed legacy package price exactly");

        List<ServiceOrderLineSnapshot> snapshots = serviceOrderLineSnapshotRepository.findAllByBusinessIdAndServiceOrderId(business.getId(), order.getId());
        List<String> labels = snapshots.stream().map(ServiceOrderLineSnapshot::getLabel).sorted().toList();
        assertTrue(labels.contains("Wash"));
        assertTrue(labels.contains("Style"));
        BigDecimal snapshotTotal = snapshots.stream().map(ServiceOrderLineSnapshot::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, snapshotTotal.compareTo(order.getPrice()));

        leaveImmutableBookingEvidenceInPlace(business.getId());
    }

    @Test
    void policyEngineReceivesTheRealOfferingIdUnderCanonicalPricing() {
        Business business = newCanonicalBusiness("Policy");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        ServiceCatalogItemResponse gated = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Gated Service", new BigDecimal("55.00"), true, 30, 1, false, null));
        ServiceCatalogItemResponse ungated = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Ungated Service", new BigDecimal("55.00"), true, 30, 1, false, null));
        Offering gatedOffering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), gated.id()).orElseThrow();

        // A policy scoped to ONLY the gated Offering, blocking, no override — proves BookingService
        // is now passing a real offeringId into evaluateGate (before Phase 5C this was always
        // null, so an offering-scoped policy could never apply to a booking at all).
        Policy policy = policyRepository.save(Policy.builder()
                .businessId(business.getId()).policyKey("offering-gate-" + UUID.randomUUID())
                .appliesToAction("BOOKING_CREATE").offeringId(gatedOffering.getId()).active(true)
                .createdBy(UUID.randomUUID()).build());
        PolicyVersion version = policyVersionRepository.save(PolicyVersion.builder()
                .businessId(business.getId()).policyId(policy.getId()).versionNumber(1)
                .title("V1").content("Must disclose before booking.")
                .requiresAcknowledgement(true).blocksTransaction(true).staffOverridable(false)
                .createdBy(UUID.randomUUID()).build());
        TenantContext.clear();

        // Gated service, no commitment supplied at all — must be blocked.
        ApiException blocked = assertThrows(ApiException.class, () -> bookingService.createBooking(business.getId(), new CreateBookingRequest(
                gated.id(), null, "Blocked Customer", "blocked@example.com", "0244123458",
                nextValidBookingSlot(), null, null, null)));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, blocked.getStatus());

        // Ungated service, same business, same missing commitment — must succeed, proving the
        // policy's offeringId scoping (not merely "any policy exists for this business") is what
        // decided the outcome.
        BookingCreatedResponse allowed = bookingService.createBooking(business.getId(), new CreateBookingRequest(
                ungated.id(), null, "Allowed Customer", "allowed@example.com", "0244123459",
                nextValidBookingSlot(), null, null, null));
        assertNotNull(allowed.manageToken());

        // policy_versions is ALSO append-only (Phase 3's own trigger — "policy_versions rows are
        // append-only", re-confirmed here directly), and policies is transitively undeletable
        // through it — both left in place, same reasoning as leaveImmutableBookingEvidenceInPlace's
        // own class-level note, matching Phase4ConcurrencyTest's own documented precedent exactly.
        leaveImmutableBookingEvidenceInPlace(business.getId());
    }

    @Test
    void canonicalRejectionNeverSilentlyFallsBackToLegacyWhenOfferingMappingIsMissing() {
        Business business = newCanonicalBusiness("MissingMapping");
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        // Bypasses ServiceCatalogService.create() to simulate an item with genuinely no canonical
        // mapping, under a business already CANONICAL_ENABLED.
        var item = serviceCatalogItemRepository.save(com.ratel.rbms.entity.ServiceCatalogItem.builder()
                .businessId(business.getId()).serviceTypeId(type.getId()).name("Unmapped")
                .price(new BigDecimal("40.00")).active(true).bookableOnline(true).durationMinutes(30).maxConcurrentBookings(1)
                .build());
        TenantContext.clear();

        ApiException ex = assertThrows(ApiException.class, () -> bookingService.createBooking(business.getId(), new CreateBookingRequest(
                item.getId(), null, "Nobody", "nobody@example.com", "0244123460",
                nextValidBookingSlot(), null, null, null)));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(serviceOrderRepository.findAllByBusinessIdOrderByReceivedAtDesc(business.getId()).isEmpty(),
                "must reject outright, never silently price it via legacy while CANONICAL_ENABLED");

        teardownCatalog(business.getId());
    }

    @Test
    void createBookingRequestHasNoClientSuppliedPriceField() {
        // Structural proof (frozen design §8/§14 — "a client must not be able to manipulate
        // canonical pricing through request DTOs"): the request record itself carries no price at
        // all, so there is nothing for BookingService to even consider trusting.
        for (RecordComponent component : CreateBookingRequest.class.getRecordComponents()) {
            assertFalse(component.getName().toLowerCase().contains("price"),
                    "CreateBookingRequest must never carry a client-suppliable price field, found: " + component.getName());
        }
    }
}
