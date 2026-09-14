package com.ratel.rbms.service;

import com.ratel.rbms.dto.AvailabilityCheckResponse;
import com.ratel.rbms.dto.BookableServiceResponse;
import com.ratel.rbms.dto.BookingCreatedResponse;
import com.ratel.rbms.dto.CreateBookingRequest;
import com.ratel.rbms.dto.CreateStaffBookingRequest;
import com.ratel.rbms.dto.ServiceCatalogItemRequest;
import com.ratel.rbms.dto.ServiceCatalogItemResponse;
import com.ratel.rbms.dto.ServicePackageItemRequest;
import com.ratel.rbms.dto.ServicePackageRequest;
import com.ratel.rbms.dto.ServicePackageResponse;
import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.PackageComponent;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.enums.BookingCutoverState;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.OfferingBookingConfigRepository;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.PackageComponentRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServicePackageItemRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5C — targeted correction: real-PostgreSQL proof that a legacy
 * package mutation can never leave a business trusting stale canonical package data.
 *
 * <p>Invariant under test: a business must never remain {@code CANONICAL_ENABLED} while a legacy
 * package mutation has made its canonical package composition potentially stale.
 */
@SpringBootTest
class Phase5CPackageMutationSafetyTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private ServicePackageRepository servicePackageRepository;
    @Autowired private ServicePackageItemRepository servicePackageItemRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private OfferingBookingConfigRepository offeringBookingConfigRepository;
    @Autowired private PackageComponentRepository packageComponentRepository;
    @Autowired private ServiceCatalogService serviceCatalogService;
    @Autowired private ServicePackageService servicePackageService;
    @Autowired private PackageContentBackfillService packageContentBackfillService;
    @Autowired private Stage1VerificationService stage1VerificationService;
    @Autowired private BookingCutoverStateResolver bookingCutoverStateResolver;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ServiceOrderRepository serviceOrderRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private static final java.util.concurrent.atomic.AtomicInteger SLOT_COUNTER = new java.util.concurrent.atomic.AtomicInteger();

    private static Instant nextValidBookingSlot() {
        int slot = SLOT_COUNTER.getAndIncrement() % 8;
        ZonedDateTime zdt = ZonedDateTime.now(ZoneOffset.UTC).plusDays(3)
                .withHour(9).withMinute(0).withSecond(0).withNano(0).plusHours(slot);
        while (zdt.getDayOfWeek() == DayOfWeek.SUNDAY) zdt = zdt.plusDays(1);
        return zdt.toInstant();
    }

    private record Fixture(Business business, ServiceType type, ServiceCatalogItemResponse item, ServicePackageResponse pkg) {
    }

    /** A fully-verified, CANONICAL_ENABLED business with one active+bookableOnline package, correctly backfilled. */
    private Fixture newCanonicalEnabledFixture(String label) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Business business = businessRepository.save(Business.builder()
                .name("Phase5C Mutation Safety " + label + " " + unique)
                .slug("phase5c-mutsafe-" + label.toLowerCase() + "-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Pkgs").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Mutation Safety Item", new BigDecimal("20.00"), false, 20, 1, false, null));
        ServicePackageResponse pkg = servicePackageService.create(new ServicePackageRequest(
                type.getId(), "Mutation Safety Pack", null, new BigDecimal("20.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(item.id(), 1)), null));
        packageContentBackfillService.backfillPackage(business.getId(), pkg.id(), false);
        TenantContext.clear();

        VerificationResult clean = stage1VerificationService.verifyBusiness(business.getId());
        assertTrue(clean.clean(), "fixture precondition: must start genuinely clean: " + clean.mismatches());
        bookingCutoverStateResolver.applyVerificationOutcome(business.getId(), clean);
        bookingCutoverStateResolver.enableCanonical(business.getId());
        assertEquals(BookingCutoverState.CANONICAL_ENABLED, bookingCutoverStateResolver.resolve(business.getId()));

        return new Fixture(business, type, item, pkg);
    }

    // ==================== The exact blocker ====================

    @Test
    void priceOnlyEditThroughRealServiceInvalidatesCanonicalEnabledImmediately() {
        Fixture fx = newCanonicalEnabledFixture("PriceOnly");
        TenantContext.setBusinessId(fx.business().getId());

        // A REAL price-only edit through the actual ServicePackageService.update() path — same
        // item, same quantity, only price differs.
        servicePackageService.update(fx.pkg().id(), new ServicePackageRequest(
                fx.type().getId(), fx.pkg().name(), null, new BigDecimal("25.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(fx.item().id(), 1)), null));
        TenantContext.clear();

        // Fresh read, independent of any Java-level cache — proves the real committed DB state.
        BookingCutoverState stateAfter = bookingCutoverStateResolver.resolve(fx.business().getId());
        assertEquals(BookingCutoverState.CANONICAL_DATA_INVALID, stateAfter,
                "a price-only edit still replaces every ServicePackageItem row, orphaning the canonical mapping — must invalidate immediately");
    }

    @Test
    void afterInvalidationEveryBookingSurfaceFallsBackToLegacy() {
        Fixture fx = newCanonicalEnabledFixture("Fallback");
        TenantContext.setBusinessId(fx.business().getId());
        servicePackageService.update(fx.pkg().id(), new ServicePackageRequest(
                fx.type().getId(), fx.pkg().name(), null, new BigDecimal("30.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(fx.item().id(), 1)), null));
        TenantContext.clear();
        assertEquals(BookingCutoverState.CANONICAL_DATA_INVALID, bookingCutoverStateResolver.resolve(fx.business().getId()));

        // Listing: must show the LEGACY price (30.00), not any stale canonical Offering price.
        List<BookableServiceResponse> listed = bookingService.listBookableServices(fx.business().getId());
        BookableServiceResponse listedPkg = listed.stream().filter(s -> fx.pkg().id().equals(s.packageId())).findFirst().orElseThrow();
        assertEquals(0, listedPkg.price().compareTo(new BigDecimal("30.00")));

        // Detail — thin filter over listing, inherits automatically.
        assertTrue(bookingService.getBookableServiceDetail(fx.business().getId(), fx.pkg().id()).isPresent());

        // Availability.
        AvailabilityCheckResponse availability = bookingService.checkAvailability(fx.business().getId(), null, fx.pkg().id(), nextValidBookingSlot());
        assertTrue(availability.available());

        // Public booking creation — must charge the legacy price and carry no canonical pointer.
        BookingCreatedResponse created = bookingService.createBooking(fx.business().getId(), new CreateBookingRequest(
                null, fx.pkg().id(), "Fallback Public Customer", "fallback-public@example.com", "0244123410",
                nextValidBookingSlot(), null, null, null, null, null));
        Booking booking = bookingRepository.findByManageToken(created.manageToken()).orElseThrow();
        ServiceOrder order = serviceOrderRepository.findById(booking.getServiceOrderId()).orElseThrow();
        assertEquals(0, order.getPrice().compareTo(new BigDecimal("30.00")));
        assertNull(order.getOfferingId(), "a legacy-fallback order must carry no canonical tracking pointer");

        // Staff booking creation — same fallback, same price.
        TenantContext.setBusinessId(fx.business().getId());
        BookingCreatedResponse staffCreated = bookingService.createStaffBooking(new CreateStaffBookingRequest(
                null, fx.pkg().id(), null, "Fallback Staff Customer", "fallback-staff@example.com", "0244123411",
                nextValidBookingSlot(), null, null, null, "UNPAID", null));
        TenantContext.clear();
        Booking staffBooking = bookingRepository.findByManageToken(staffCreated.manageToken()).orElseThrow();
        ServiceOrder staffOrder = serviceOrderRepository.findById(staffBooking.getServiceOrderId()).orElseThrow();
        assertEquals(0, staffOrder.getPrice().compareTo(new BigDecimal("30.00")));
        assertNull(staffOrder.getOfferingId());

        // AI: AiToolService.createBooking/listBookableServices/checkAvailability all call these
        // EXACT same BookingService methods (confirmed by source, unchanged in this correction) —
        // there is no independent AI code path that could see anything different.
    }

    @Test
    void fullRecoveryScenario_invalidToRepairToVerifiedToExplicitEnable() {
        Fixture fx = newCanonicalEnabledFixture("Recovery");
        UUID businessId = fx.business().getId();

        TenantContext.setBusinessId(businessId);
        servicePackageService.update(fx.pkg().id(), new ServicePackageRequest(
                fx.type().getId(), fx.pkg().name(), null, new BigDecimal("40.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(fx.item().id(), 1)), null));
        TenantContext.clear();
        assertEquals(BookingCutoverState.CANONICAL_DATA_INVALID, bookingCutoverStateResolver.resolve(businessId));

        // Repair. update() replaced the ServicePackageItem row wholesale (new id, same content) —
        // a real, already-proven consequence of ServicePackageService.update()'s own delete-then-
        // rebuild semantics (see PackageContentBackfillServiceTest's own
        // everyLegacyPackageEditOrphansAllPreviouslyBackfilledComponentsForThatPackage). Re-running
        // the backfill reconciles by creating a fresh component for the new item row; the OLD
        // component is now a genuine orphan and is, BY DESIGN, never auto-deleted (Revision 4
        // §3/§6 — "never silently delete or ignore orphan"). A real repair therefore has two real
        // steps: reconcile (backfill), then an explicit operator decision to remove the orphan —
        // simulated here exactly as a future admin action would perform it.
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(businessId, fx.pkg().id()).orElseThrow();
        packageContentBackfillService.backfillPackage(businessId, fx.pkg().id(), false);
        List<PackageComponent> orphans = packageContentBackfillService.findOrphanComponents(businessId, offering.getId());
        assertEquals(1, orphans.size(), "sanity check: exactly the expected orphan from the earlier price-edit");
        packageComponentRepository.deleteAll(orphans); // cascades away the orphan's own Option (fk_options_component)

        // Verify: Stage1VerificationService remains the sole authority.
        VerificationResult repaired = stage1VerificationService.verifyBusiness(businessId);
        assertTrue(repaired.clean(), "after repair: " + repaired.mismatches());
        bookingCutoverStateResolver.applyVerificationOutcome(businessId, repaired);
        assertEquals(BookingCutoverState.VERIFIED, bookingCutoverStateResolver.resolve(businessId),
                "recovery ends at VERIFIED, never jumps straight back to CANONICAL_ENABLED");
        assertFalse(bookingCutoverStateResolver.useCanonical(businessId));

        // Explicit, separate re-enable.
        bookingCutoverStateResolver.enableCanonical(businessId);
        assertEquals(BookingCutoverState.CANONICAL_ENABLED, bookingCutoverStateResolver.resolve(businessId));

        // Final booking proves the canonical path is genuinely used again (new price, offeringId set).
        BookingCreatedResponse created = bookingService.createBooking(businessId, new CreateBookingRequest(
                null, fx.pkg().id(), "Recovered Customer", "recovered@example.com", "0244123412",
                nextValidBookingSlot(), null, null, null, null, null));
        Booking booking = bookingRepository.findByManageToken(created.manageToken()).orElseThrow();
        ServiceOrder order = serviceOrderRepository.findById(booking.getServiceOrderId()).orElseThrow();
        assertEquals(0, order.getPrice().compareTo(new BigDecimal("40.00")));
        assertEquals(offering.getId(), order.getOfferingId());
    }

    // ==================== Precision: not over-invalidated ====================

    @Test
    void tenantIsolation_businessBUnaffectedByBusinessAsMutation() {
        Fixture fxA = newCanonicalEnabledFixture("TenantA");
        Fixture fxB = newCanonicalEnabledFixture("TenantB");

        TenantContext.setBusinessId(fxA.business().getId());
        servicePackageService.update(fxA.pkg().id(), new ServicePackageRequest(
                fxA.type().getId(), fxA.pkg().name(), null, new BigDecimal("99.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(fxA.item().id(), 1)), null));
        TenantContext.clear();

        assertEquals(BookingCutoverState.CANONICAL_DATA_INVALID, bookingCutoverStateResolver.resolve(fxA.business().getId()));
        assertEquals(BookingCutoverState.CANONICAL_ENABLED, bookingCutoverStateResolver.resolve(fxB.business().getId()),
                "business B's cutover state must be completely untouched by business A's mutation");
    }

    @Test
    void deactivatingDoesNotInvalidateButReactivatingDoes() {
        Fixture fx = newCanonicalEnabledFixture("Deactivate");
        TenantContext.setBusinessId(fx.business().getId());

        servicePackageService.setActive(fx.pkg().id(), false);
        assertEquals(BookingCutoverState.CANONICAL_ENABLED, bookingCutoverStateResolver.resolve(fx.business().getId()),
                "deactivating only ever REMOVES a package from the verified surface — must not invalidate");

        servicePackageService.setActive(fx.pkg().id(), true);
        TenantContext.clear();
        assertEquals(BookingCutoverState.CANONICAL_DATA_INVALID, bookingCutoverStateResolver.resolve(fx.business().getId()),
                "reactivating can reintroduce staleness — must invalidate");
    }

    @Test
    void creatingANonBookableOnlinePackageNeverInvalidates() {
        Fixture fx = newCanonicalEnabledFixture("QuietCreate");
        TenantContext.setBusinessId(fx.business().getId());

        // bookableOnline=false — never part of the population Stage1VerificationService or
        // BookingService's canonical listing ever look at.
        servicePackageService.create(new ServicePackageRequest(
                fx.type().getId(), "Internal Only Pack " + UUID.randomUUID(), null, new BigDecimal("15.00"), false, 30, 1,
                List.of(new ServicePackageItemRequest(fx.item().id(), 1)), null));
        TenantContext.clear();

        assertEquals(BookingCutoverState.CANONICAL_ENABLED, bookingCutoverStateResolver.resolve(fx.business().getId()),
                "a non-bookable-online package can never affect canonical booking trust — must not invalidate");
    }

    @Test
    void creatingAnActiveBookableOnlinePackageInvalidates() {
        Fixture fx = newCanonicalEnabledFixture("LoudCreate");
        TenantContext.setBusinessId(fx.business().getId());

        servicePackageService.create(new ServicePackageRequest(
                fx.type().getId(), "New Public Pack " + UUID.randomUUID(), null, new BigDecimal("15.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(fx.item().id(), 1)), null));
        TenantContext.clear();

        assertEquals(BookingCutoverState.CANONICAL_DATA_INVALID, bookingCutoverStateResolver.resolve(fx.business().getId()),
                "a brand-new active+bookableOnline package has zero backfilled components — must invalidate");
    }

    // ==================== Rollback ====================

    @Test
    void downstreamFailureDuringUpdateRollsBackTheInvalidationToo_realDatabaseProof() {
        Fixture fx = newCanonicalEnabledFixture("Rollback");
        UUID businessId = fx.business().getId();
        UUID packageId = fx.pkg().id();

        List<PackageComponent> componentsBefore = packageComponentRepository.findAllByBusinessIdAndOfferingId(
                businessId, offeringRepository.findByBusinessIdAndLegacyServicePackageId(businessId, packageId).orElseThrow().getId());
        List<com.ratel.rbms.entity.ServicePackageItem> itemsBefore = servicePackageItemRepository.findAllByPackageId(packageId);

        TenantContext.setBusinessId(businessId);
        // A real downstream failure: one selected service doesn't exist — saveItems() throws
        // AFTER the invalidation call already ran, inside the SAME @Transactional method.
        ApiException ex = assertThrows(ApiException.class, () -> servicePackageService.update(packageId, new ServicePackageRequest(
                fx.type().getId(), fx.pkg().name(), null, new BigDecimal("77.00"), true, 30, 1,
                List.of(new ServicePackageItemRequest(UUID.randomUUID(), 1)), null)));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
        TenantContext.clear();

        // ---- Fresh reads after the real rollback ----
        assertEquals(BookingCutoverState.CANONICAL_ENABLED, bookingCutoverStateResolver.resolve(businessId),
                "the invalidation write must roll back along with everything else in the failed transaction");

        var pkgAfter = servicePackageRepository.findByIdAndBusinessId(packageId, businessId).orElseThrow();
        assertEquals(0, pkgAfter.getPrice().compareTo(fx.pkg().price()), "legacy package price must be unchanged");

        List<com.ratel.rbms.entity.ServicePackageItem> itemsAfter = servicePackageItemRepository.findAllByPackageId(packageId);
        assertEquals(itemsBefore.stream().map(com.ratel.rbms.entity.ServicePackageItem::getId).sorted().toList(),
                itemsAfter.stream().map(com.ratel.rbms.entity.ServicePackageItem::getId).sorted().toList(),
                "ServicePackageItem rows must be exactly unchanged — the delete-then-rebuild must never have partially applied");

        List<PackageComponent> componentsAfter = packageComponentRepository.findAllByBusinessIdAndOfferingId(
                businessId, offeringRepository.findByBusinessIdAndLegacyServicePackageId(businessId, packageId).orElseThrow().getId());
        assertEquals(componentsBefore.stream().map(PackageComponent::getId).sorted().toList(),
                componentsAfter.stream().map(PackageComponent::getId).sorted().toList(),
                "canonical PackageComponent rows must be exactly unchanged");
    }

    // ==================== Concurrency ====================

    @Test
    void concurrentPackageUpdateAndEnableCanonical_neverEndsWithCanonicalEnabledOverInvalidData() throws Exception {
        // Starts from VERIFIED (not yet CANONICAL_ENABLED) specifically so BOTH race participants
        // have a real, legitimate action available: update() (always allowed) racing against
        // enableCanonical() (requires VERIFIED) — proving the row lock genuinely serializes them
        // regardless of which one wins.
        Fixture fx = newCanonicalEnabledFixture("Concurrency");
        bookingCutoverStateResolver.revertToVerified(fx.business().getId());
        assertEquals(BookingCutoverState.VERIFIED, bookingCutoverStateResolver.resolve(fx.business().getId()));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> updateAttempt = pool.submit(() -> {
                TenantContext.setBusinessId(fx.business().getId());
                try {
                    servicePackageService.update(fx.pkg().id(), new ServicePackageRequest(
                            fx.type().getId(), fx.pkg().name(), null, new BigDecimal("55.00"), true, 30, 1,
                            List.of(new ServicePackageItemRequest(fx.item().id(), 1)), null));
                } finally {
                    TenantContext.clear();
                }
            });
            Future<?> enableAttempt = pool.submit(() -> {
                try {
                    bookingCutoverStateResolver.enableCanonical(fx.business().getId());
                } catch (ApiException ignored) {
                    // Legitimate outcome if update's invalidation won the race first.
                }
            });
            updateAttempt.get(30, TimeUnit.SECONDS);
            enableAttempt.get(30, TimeUnit.SECONDS);

            BookingCutoverState finalState = bookingCutoverStateResolver.resolve(fx.business().getId());
            assertNotEquals(BookingCutoverState.CANONICAL_ENABLED, finalState,
                    "regardless of interleaving, the business must never end up CANONICAL_ENABLED over data an in-flight mutation just invalidated");
            assertTrue(finalState == BookingCutoverState.CANONICAL_DATA_INVALID || finalState == BookingCutoverState.NOT_READY,
                    "final state must be a legitimate, safe outcome, got " + finalState);
        } finally {
            pool.shutdownNow();
        }
    }
}
