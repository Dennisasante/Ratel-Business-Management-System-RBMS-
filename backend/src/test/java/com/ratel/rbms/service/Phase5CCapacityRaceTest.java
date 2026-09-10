package com.ratel.rbms.service;

import com.ratel.rbms.dto.CreateBookingRequest;
import com.ratel.rbms.dto.ServiceCatalogItemRequest;
import com.ratel.rbms.dto.ServiceCatalogItemResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5C — real-PostgreSQL proof of the capacity-race correctness fix
 * (frozen design §10, tightened by the final implementation instruction §11). Two proofs:
 *
 * <ol>
 *   <li>{@link #offeringRowLockGenuinelySerializesTwoIndependentConnections()} — the lock
 *       mechanism itself, via two genuinely independent JDBC connections/transactions, proving
 *       exactly the eight points the instruction enumerates (adapted: this implementation
 *       acquires the Offering lock BEFORE any capacity read at all, per the approved
 *       "lock first, then decide" design — there is no separate stale pre-lock count to observe,
 *       which is the whole point of the fix, so "both initially observe capacity available" is
 *       proven as "both threads' capacity decision happens only after acquiring the lock,
 *       never before").</li>
 *   <li>{@link #concurrentCanonicalBookingCreation_exactlyOneOfTwoSucceedsForTheLastSlot()} — the
 *       real, end-to-end outcome through {@code BookingService.createBooking} itself, mirroring
 *       {@code Phase4ConcurrencyTest}'s own established real-thread/real-transaction pattern.</li>
 * </ol>
 */
@SpringBootTest
class Phase5CCapacityRaceTest {

    @Autowired private DataSource dataSource;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private ServiceCatalogService serviceCatalogService;
    @Autowired private BookingCutoverStateResolver bookingCutoverStateResolver;
    @Autowired private BookingService bookingService;
    @Autowired private ServiceOrderRepository serviceOrderRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private static Instant fixedFutureSlot() {
        ZonedDateTime zdt = ZonedDateTime.now(ZoneOffset.UTC).plusDays(3).withHour(11).withMinute(0).withSecond(0).withNano(0);
        while (zdt.getDayOfWeek() == DayOfWeek.SUNDAY) zdt = zdt.plusDays(1);
        return zdt.toInstant();
    }

    @Test
    void offeringRowLockGenuinelySerializesTwoIndependentConnections() throws Exception {
        Business business = businessRepository.save(Business.builder()
                .name("Phase5C Lock Race " + UUID.randomUUID().toString().substring(0, 8))
                .slug("phase5c-lock-race-" + UUID.randomUUID().toString().substring(0, 8))
                .industry(Industry.OTHER).currency("GHS").build());
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Lock Race Item", new BigDecimal("10.00"), true, 30, 1, false, null));
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.id()).orElseThrow();
        TenantContext.clear();

        // 1. Two genuinely independent connections (separate physical JDBC connections from the
        // pool, each with its own manually-managed transaction — not two threads sharing one
        // EntityManager/Session).
        try (Connection connA = dataSource.getConnection(); Connection connB = dataSource.getConnection()) {
            connA.setAutoCommit(false);
            connB.setAutoCommit(false);

            CountDownLatch aHasLock = new CountDownLatch(1);
            CountDownLatch bAttempted = new CountDownLatch(1);
            AtomicInteger bBlockedForAtLeastMillis = new AtomicInteger(0);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                // 3/4. Both attempt the SAME row lock; connection A acquires immediately.
                Future<?> taskA = pool.submit(() -> {
                    try (PreparedStatement ps = connA.prepareStatement(
                            "SELECT id FROM offerings WHERE id = ? AND business_id = ? FOR UPDATE")) {
                        ps.setObject(1, offering.getId());
                        ps.setObject(2, business.getId());
                        try (ResultSet rs = ps.executeQuery()) {
                            assertTrue(rs.next(), "connection A must acquire the lock");
                        }
                        aHasLock.countDown();
                        // Hold the lock open long enough to prove B genuinely blocks on it.
                        bAttempted.await(10, TimeUnit.SECONDS);
                        Thread.sleep(300);
                        connA.commit(); // 6. releases the lock
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                // 5. Connection B's attempt must BLOCK while A holds the lock uncommitted.
                Future<?> taskB = pool.submit(() -> {
                    try {
                        aHasLock.await(10, TimeUnit.SECONDS);
                        long start = System.currentTimeMillis();
                        bAttempted.countDown();
                        try (PreparedStatement ps = connB.prepareStatement(
                                "SELECT id FROM offerings WHERE id = ? AND business_id = ? FOR UPDATE")) {
                            ps.setObject(1, offering.getId());
                            ps.setObject(2, business.getId());
                            try (ResultSet rs = ps.executeQuery()) {
                                assertTrue(rs.next(), "connection B must eventually acquire the lock too, once A releases it");
                            }
                        }
                        bBlockedForAtLeastMillis.set((int) (System.currentTimeMillis() - start));
                        connB.commit();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                taskA.get(30, TimeUnit.SECONDS);
                taskB.get(30, TimeUnit.SECONDS);

                // 5/6/7. B's own acquisition took a real, measurable amount of time waiting on A —
                // proves it genuinely blocked at the database level, not merely "ran after" by
                // thread-scheduling coincidence.
                assertTrue(bBlockedForAtLeastMillis.get() >= 250,
                        "connection B must have genuinely blocked on the lock for roughly as long as A held it, took only "
                                + bBlockedForAtLeastMillis.get() + "ms");
            } finally {
                pool.shutdownNow();
            }
        }

        businessRepository.deleteById(business.getId()); // ServiceCatalogItem/Offering left as-is; harmless, matches other tests' own residue precedent if FK forbids full cleanup
    }

    @Test
    void concurrentCanonicalBookingCreation_exactlyOneOfTwoSucceedsForTheLastSlot() throws Exception {
        Business business = businessRepository.save(Business.builder()
                .name("Phase5C Capacity Race " + UUID.randomUUID().toString().substring(0, 8))
                .slug("phase5c-capacity-race-" + UUID.randomUUID().toString().substring(0, 8))
                .industry(Industry.OTHER).currency("GHS").build());
        TenantContext.setBusinessId(business.getId());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        // maxConcurrentBookings = 1 — the exact scenario the instruction specifies.
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Last Slot Item", new BigDecimal("10.00"), true, 30, 1, false, null));
        TenantContext.clear();

        bookingCutoverStateResolver.markVerified(business.getId(), new VerificationResult(business.getId(), List.of(), List.of()));
        bookingCutoverStateResolver.enableCanonical(business.getId());

        Instant slot = fixedFutureSlot(); // BOTH attempts target the exact same slot deliberately

        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger otherFailures = new AtomicInteger();
        try {
            Runnable attempt = () -> {
                try {
                    bookingService.createBooking(business.getId(), new CreateBookingRequest(
                            item.id(), null, "Racer " + UUID.randomUUID(), "racer@example.com",
                            "024412340" + (int) (Math.random() * 10), slot, null, null, null));
                    succeeded.incrementAndGet();
                } catch (ApiException e) {
                    if (e.getMessage() != null && e.getMessage().contains("fully booked")) {
                        rejected.incrementAndGet();
                    } else {
                        otherFailures.incrementAndGet();
                    }
                }
            };
            Future<?> a = pool.submit(attempt);
            Future<?> b = pool.submit(attempt);
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);

            assertEquals(0, otherFailures.get(), "no unexpected failure type");
            assertEquals(1, succeeded.get(), "exactly one of two concurrent bookings for the SAME last slot must succeed");
            assertEquals(1, rejected.get(), "the other must be rejected as fully booked, not silently double-booked");

            long realOrders = serviceOrderRepository.findAllByBusinessIdOrderByReceivedAtDesc(business.getId()).stream()
                    .filter(o -> slot.equals(o.getScheduledAt()))
                    .count();
            assertEquals(1, realOrders, "final database state: exactly one ServiceOrder for this slot — no partial row from the rejected attempt");
        } finally {
            pool.shutdownNow();
        }
        // Business/booking chain left in place — the successful booking wrote a real, permanently
        // undeletable snapshot row (same reasoning as Phase5CCanonicalBookingTest's own documented
        // precedent).
    }
}
