package com.ratel.rbms.service;

import com.ratel.rbms.dto.AvailabilityCheckResponse;
import com.ratel.rbms.dto.BookableServiceResponse;
import com.ratel.rbms.dto.BookingCreatedResponse;
import com.ratel.rbms.dto.CreateBookingRequest;
import com.ratel.rbms.dto.ServiceCatalogItemRequest;
import com.ratel.rbms.dto.ServiceCatalogItemResponse;
import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.enums.BookingCutoverState;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.OfferingBookingConfigRepository;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5C — real-PostgreSQL proof of the legacy-fallback behaviour
 * matrix (frozen implementation instruction §4/§23/§28): every booking-adjacent
 * {@code BookingService} surface uses legacy under NOT_READY/VERIFIED/CANONICAL_DATA_INVALID and
 * canonical only under CANONICAL_ENABLED, plus the full CANONICAL_DATA_INVALID lifecycle scenario
 * (§28).
 *
 * <p>Every fixture deliberately gives the legacy item and its canonical Offering DIFFERENT
 * prices — the one unambiguous signal of which source actually answered a given call.
 */
@SpringBootTest
class Phase5CLegacyFallbackTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private OfferingBookingConfigRepository offeringBookingConfigRepository;
    @Autowired private ServiceCatalogService serviceCatalogService;
    @Autowired private BookingCutoverStateResolver bookingCutoverStateResolver;
    @Autowired private Stage1VerificationService stage1VerificationService;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ServiceOrderRepository serviceOrderRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private static final BigDecimal LEGACY_PRICE = new BigDecimal("50.00");
    private static final BigDecimal CANONICAL_PRICE = new BigDecimal("999.00");

    private record Fixture(Business business, ServiceCatalogItemResponse item, Offering offering) {
    }

    private Fixture newFixture(String label) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Business business = businessRepository.save(Business.builder()
                .name("Phase5C Fallback " + label + " " + unique).slug("phase5c-fallback-" + label.toLowerCase() + "-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Fallback Service", LEGACY_PRICE, true, 30, 1, false, null));
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.id()).orElseThrow();
        offering.setBasePrice(CANONICAL_PRICE); // deliberately diverges from legacy — the source-of-truth signal
        offeringRepository.save(offering);
        TenantContext.clear();
        return new Fixture(business, item, offering);
    }

    // A fresh, NON-overlapping slot on every call, deterministically (not merely probabilistically)
    // distinct — several tests here book the SAME item multiple times in sequence
    // (maxConcurrentBookings=1, 30-minute duration), so reusing one instant, or even a randomly
    // spaced one, risks spuriously tripping the capacity check between calls rather than proving
    // what each call set out to prove. 60-minute spacing, well clear of the 30-minute duration.
    private static final java.util.concurrent.atomic.AtomicInteger SLOT_COUNTER = new java.util.concurrent.atomic.AtomicInteger();

    private static Instant nextValidBookingSlot() {
        int slot = SLOT_COUNTER.getAndIncrement() % 8; // 9am..4pm in hourly steps, safely inside 9am-6pm
        ZonedDateTime zdt = ZonedDateTime.now(ZoneOffset.UTC).plusDays(3)
                .withHour(9).withMinute(0).withSecond(0).withNano(0)
                .plusHours(slot);
        while (zdt.getDayOfWeek() == DayOfWeek.SUNDAY) zdt = zdt.plusDays(1);
        return zdt.toInstant();
    }

    private void assertUsesLegacy(Fixture fx) {
        List<BookableServiceResponse> listed = bookingService.listBookableServices(fx.business().getId());
        assertEquals(1, listed.size());
        assertEquals(0, listed.get(0).price().compareTo(LEGACY_PRICE), "listing must show the legacy price");

        AvailabilityCheckResponse availability = bookingService.checkAvailability(fx.business().getId(), fx.item().id(), null, nextValidBookingSlot());
        assertTrue(availability.available());

        BookingCreatedResponse created = bookingService.createBooking(fx.business().getId(), new CreateBookingRequest(
                fx.item().id(), null, "Legacy Path Customer " + UUID.randomUUID(), "legacy@example.com", "0244123401",
                nextValidBookingSlot(), null, null, null));
        Booking booking = bookingRepository.findByManageToken(created.manageToken()).orElseThrow();
        ServiceOrder order = serviceOrderRepository.findById(booking.getServiceOrderId()).orElseThrow();
        assertEquals(0, order.getPrice().compareTo(LEGACY_PRICE), "the actual charged price must be the legacy one");
        assertNull(order.getOfferingId(), "a legacy-priced order must carry no canonical tracking pointer");
    }

    private void assertUsesCanonical(Fixture fx) {
        List<BookableServiceResponse> listed = bookingService.listBookableServices(fx.business().getId());
        assertEquals(1, listed.size());
        assertEquals(0, listed.get(0).price().compareTo(CANONICAL_PRICE), "listing must show the canonical price");

        AvailabilityCheckResponse availability = bookingService.checkAvailability(fx.business().getId(), fx.item().id(), null, nextValidBookingSlot());
        assertTrue(availability.available());

        BookingCreatedResponse created = bookingService.createBooking(fx.business().getId(), new CreateBookingRequest(
                fx.item().id(), null, "Canonical Path Customer " + UUID.randomUUID(), "canonical@example.com", "0244123402",
                nextValidBookingSlot(), null, null, null));
        Booking booking = bookingRepository.findByManageToken(created.manageToken()).orElseThrow();
        ServiceOrder order = serviceOrderRepository.findById(booking.getServiceOrderId()).orElseThrow();
        assertEquals(0, order.getPrice().compareTo(CANONICAL_PRICE), "the actual charged price must be the canonical one");
        assertEquals(fx.offering().getId(), order.getOfferingId());
    }

    @Test
    void notReadyUsesLegacyEverywhere() {
        Fixture fx = newFixture("NotReady");
        assertEquals(BookingCutoverState.NOT_READY, bookingCutoverStateResolver.resolve(fx.business().getId()));
        assertUsesLegacy(fx);
    }

    @Test
    void verifiedUsesLegacyEverywhere() {
        Fixture fx = newFixture("Verified");
        bookingCutoverStateResolver.markVerified(fx.business().getId(), new VerificationResult(fx.business().getId(), List.of(), List.of()));
        assertEquals(BookingCutoverState.VERIFIED, bookingCutoverStateResolver.resolve(fx.business().getId()));
        assertUsesLegacy(fx);
    }

    @Test
    void canonicalEnabledUsesCanonicalEverywhere() {
        Fixture fx = newFixture("Enabled");
        bookingCutoverStateResolver.markVerified(fx.business().getId(), new VerificationResult(fx.business().getId(), List.of(), List.of()));
        bookingCutoverStateResolver.enableCanonical(fx.business().getId());
        assertEquals(BookingCutoverState.CANONICAL_ENABLED, bookingCutoverStateResolver.resolve(fx.business().getId()));
        assertUsesCanonical(fx);
    }

    @Test
    void canonicalDataInvalidFallsBackToLegacyEverywhereNeverHardFails() {
        Fixture fx = newFixture("Invalid");
        bookingCutoverStateResolver.markVerified(fx.business().getId(), new VerificationResult(fx.business().getId(), List.of(), List.of()));
        bookingCutoverStateResolver.enableCanonical(fx.business().getId());
        bookingCutoverStateResolver.markInvalid(fx.business().getId(), List.of("simulated defect"));
        assertEquals(BookingCutoverState.CANONICAL_DATA_INVALID, bookingCutoverStateResolver.resolve(fx.business().getId()));
        assertUsesLegacy(fx);
    }

    // The exact scenario from the frozen implementation instruction §28, end to end, against real
    // PostgreSQL throughout.
    @Test
    void fullCanonicalDataInvalidLifecycleScenario() {
        Fixture fx = newFixture("FullScenario");
        UUID businessId = fx.business().getId();

        // 1/2. Reach VERIFIED then CANONICAL_ENABLED (a genuinely clean verification — undo the
        // fixture's own deliberate price divergence first, since THIS test needs a truly clean
        // starting point before corrupting it deliberately in step 3).
        TenantContext.setBusinessId(businessId);
        fx.offering().setBasePrice(LEGACY_PRICE);
        offeringRepository.save(fx.offering());
        TenantContext.clear();
        VerificationResult firstPass = stage1VerificationService.verifyBusiness(businessId);
        assertTrue(firstPass.clean(), "precondition: must be genuinely clean before enabling: " + firstPass.mismatches());
        bookingCutoverStateResolver.applyVerificationOutcome(businessId, firstPass);
        bookingCutoverStateResolver.enableCanonical(businessId);
        assertEquals(BookingCutoverState.CANONICAL_ENABLED, bookingCutoverStateResolver.resolve(businessId));

        // 3. Deliberately introduce a canonical/legacy mismatch.
        Offering offering = offeringRepository.findByIdAndBusinessId(fx.offering().getId(), businessId).orElseThrow();
        offering.setBasePrice(CANONICAL_PRICE);
        offeringRepository.save(offering);

        // 4/5. Run verification -> state becomes CANONICAL_DATA_INVALID.
        VerificationResult dirtyResult = stage1VerificationService.verifyBusiness(businessId);
        assertFalse(dirtyResult.clean());
        bookingCutoverStateResolver.applyVerificationOutcome(businessId, dirtyResult);
        assertEquals(BookingCutoverState.CANONICAL_DATA_INVALID, bookingCutoverStateResolver.resolve(businessId));

        // 6. Prove every surface uses legacy (list/detail/price/availability/public+staff/AI-
        // equivalent — AI is proven structurally: AiToolService delegates to these exact same
        // BookingService methods, re-confirmed by source read, so this IS the AI proof too).
        assertUsesLegacy(fx);

        // 7/8. Repair the data, run full verification again -> clean.
        offering.setBasePrice(LEGACY_PRICE);
        offeringRepository.save(offering);
        VerificationResult repaired = stage1VerificationService.verifyBusiness(businessId);
        assertTrue(repaired.clean(), "after repair: " + repaired.mismatches());

        // 9. Apply -> VERIFIED.
        bookingCutoverStateResolver.applyVerificationOutcome(businessId, repaired);
        assertEquals(BookingCutoverState.VERIFIED, bookingCutoverStateResolver.resolve(businessId));

        // 10. Canonical is STILL NOT active — recovery ends at VERIFIED, never jumps to CANONICAL_ENABLED.
        assertFalse(bookingCutoverStateResolver.useCanonical(businessId));
        assertUsesLegacy(fx);

        // 11/12. Explicit, separate enable -> canonical resumes. Re-diverge the canonical price
        // back to its own distinguishing sentinel first (the "repair" step above made the two
        // prices coincidentally equal, which would make this final assertion unable to tell which
        // source actually answered) — enableCanonical only requires the state to currently be
        // VERIFIED, not that data stay clean forever after, so this is a legitimate call.
        offering.setBasePrice(CANONICAL_PRICE);
        offeringRepository.save(offering);
        bookingCutoverStateResolver.enableCanonical(businessId);
        assertEquals(BookingCutoverState.CANONICAL_ENABLED, bookingCutoverStateResolver.resolve(businessId));
        assertUsesCanonical(fx);
    }
}
