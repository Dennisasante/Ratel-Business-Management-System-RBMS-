package com.ratel.rbms.service;

import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.enums.BookingCutoverState;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5C — real-PostgreSQL proof of the frozen per-business cutover
 * lifecycle (Revision 4 §4) and its concurrency guarantee (§29).
 */
@SpringBootTest
class BookingCutoverStateResolverTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private BookingCutoverStateResolver resolver;

    private UUID newBusiness() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Business business = businessRepository.save(Business.builder()
                .name("Phase5C Lifecycle " + unique).slug("phase5c-lifecycle-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
        return business.getId();
    }

    private static VerificationResult clean(UUID businessId) {
        return new VerificationResult(businessId, List.of(), List.of());
    }

    private static VerificationResult dirty(UUID businessId) {
        return new VerificationResult(businessId, List.of(new ParityMismatch(ParityMismatch.PRICE_MISMATCH, "SERVICE_CATALOG_ITEM", UUID.randomUUID(), "test")), List.of());
    }

    @AfterEach
    void cleanup() {
        // Nothing to clean beyond the Business row itself — no other table is touched by this
        // resolver, and each test creates its own throwaway business.
    }

    @Test
    void defaultsToNotReadyAndUseCanonicalIsFalse() {
        UUID businessId = newBusiness();
        assertEquals(BookingCutoverState.NOT_READY, resolver.resolve(businessId));
        assertFalse(resolver.useCanonical(businessId));
        businessRepository.deleteById(businessId);
    }

    @Test
    void cleanVerificationPromotesNotReadyToVerifiedButNotCanonical() {
        UUID businessId = newBusiness();
        resolver.markVerified(businessId, clean(businessId));
        assertEquals(BookingCutoverState.VERIFIED, resolver.resolve(businessId));
        assertFalse(resolver.useCanonical(businessId), "VERIFIED must not itself enable canonical serving — Revision 4 §4's own corrective fix");
        businessRepository.deleteById(businessId);
    }

    @Test
    void markVerifiedWithDirtyResultThrows() {
        UUID businessId = newBusiness();
        assertThrows(IllegalArgumentException.class, () -> resolver.markVerified(businessId, dirty(businessId)));
        businessRepository.deleteById(businessId);
    }

    @Test
    void enableCanonicalRequiresVerified() {
        UUID businessId = newBusiness();
        assertThrows(ApiException.class, () -> resolver.enableCanonical(businessId), "must reject NOT_READY -> CANONICAL_ENABLED directly");

        resolver.markVerified(businessId, clean(businessId));
        resolver.enableCanonical(businessId);
        assertEquals(BookingCutoverState.CANONICAL_ENABLED, resolver.resolve(businessId));
        assertTrue(resolver.useCanonical(businessId));

        businessRepository.deleteById(businessId);
    }

    @Test
    void revertToVerifiedRequiresCanonicalEnabledAndPreservesVerification() {
        UUID businessId = newBusiness();
        resolver.markVerified(businessId, clean(businessId));
        resolver.enableCanonical(businessId);

        resolver.revertToVerified(businessId);
        assertEquals(BookingCutoverState.VERIFIED, resolver.resolve(businessId));
        assertFalse(resolver.useCanonical(businessId));

        // Re-enable requires no re-verification — the pure deployment toggle promised by the design.
        resolver.enableCanonical(businessId);
        assertEquals(BookingCutoverState.CANONICAL_ENABLED, resolver.resolve(businessId));

        businessRepository.deleteById(businessId);
    }

    @Test
    void markInvalidFromNotReadyIsANoOp() {
        UUID businessId = newBusiness();
        resolver.markInvalid(businessId, List.of("irrelevant"));
        assertEquals(BookingCutoverState.NOT_READY, resolver.resolve(businessId));
        businessRepository.deleteById(businessId);
    }

    @Test
    void markInvalidFromVerifiedDemotesToNotReady() {
        UUID businessId = newBusiness();
        resolver.markVerified(businessId, clean(businessId));
        resolver.markInvalid(businessId, List.of("stale mapping"));
        assertEquals(BookingCutoverState.NOT_READY, resolver.resolve(businessId));
        businessRepository.deleteById(businessId);
    }

    @Test
    void markInvalidFromCanonicalEnabledEntersCanonicalDataInvalidAndFallsBackSafely() {
        UUID businessId = newBusiness();
        resolver.markVerified(businessId, clean(businessId));
        resolver.enableCanonical(businessId);

        resolver.markInvalid(businessId, List.of("price drift detected"));
        assertEquals(BookingCutoverState.CANONICAL_DATA_INVALID, resolver.resolve(businessId));
        assertFalse(resolver.useCanonical(businessId), "CANONICAL_DATA_INVALID must fall back to legacy, never hard-fail");

        businessRepository.deleteById(businessId);
    }

    @Test
    void recoveryFromInvalidRequiresCleanVerificationAndNeverJumpsStraightToCanonicalEnabled() {
        UUID businessId = newBusiness();
        resolver.markVerified(businessId, clean(businessId));
        resolver.enableCanonical(businessId);
        resolver.markInvalid(businessId, List.of("defect"));
        assertEquals(BookingCutoverState.CANONICAL_DATA_INVALID, resolver.resolve(businessId));

        assertThrows(IllegalArgumentException.class, () -> resolver.recoverFromInvalid(businessId, dirty(businessId)),
                "recovery must require a CLEAN verification");
        assertThrows(ApiException.class, () -> resolver.markVerified(businessId, clean(businessId)),
                "markVerified must refuse to run against CANONICAL_DATA_INVALID — recoverFromInvalid is the only door out");

        resolver.recoverFromInvalid(businessId, clean(businessId));
        assertEquals(BookingCutoverState.VERIFIED, resolver.resolve(businessId), "recovery ends at VERIFIED, never CANONICAL_ENABLED directly");
        assertFalse(resolver.useCanonical(businessId));

        // A SEPARATE, explicit action is required to actually resume canonical serving.
        resolver.enableCanonical(businessId);
        assertEquals(BookingCutoverState.CANONICAL_ENABLED, resolver.resolve(businessId));

        businessRepository.deleteById(businessId);
    }

    // ==================== Concurrency (Revision 4 §29) ====================

    @Test
    void concurrentEnableCanonicalAttempts_exactlyOneOfTenSucceeds() throws Exception {
        UUID businessId = newBusiness();
        resolver.markVerified(businessId, clean(businessId));

        int attempts = 10;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        try {
            List<Future<?>> futures = java.util.stream.IntStream.range(0, attempts)
                    .<Runnable>mapToObj(i -> () -> {
                        try {
                            resolver.enableCanonical(businessId);
                            succeeded.incrementAndGet();
                        } catch (ApiException e) {
                            conflicted.incrementAndGet();
                        }
                    })
                    .map(pool::submit)
                    .toList();
            for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);

            assertEquals(1, succeeded.get(), "exactly one of ten concurrent enableCanonical calls on the SAME business must succeed");
            assertEquals(attempts - 1, conflicted.get(), "every other concurrent attempt must fail safely (CONFLICT), never corrupt the state");
            assertEquals(BookingCutoverState.CANONICAL_ENABLED, resolver.resolve(businessId));
        } finally {
            pool.shutdownNow();
            businessRepository.deleteById(businessId);
        }
    }

    @Test
    void concurrentEnableAndRevert_neverProduceAnInvalidLifecycleState() throws Exception {
        UUID businessId = newBusiness();
        resolver.markVerified(businessId, clean(businessId));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // One thread tries to enable, the other tries to revert (which requires
            // CANONICAL_ENABLED and will fail if it runs before enable ever succeeds) —
            // whichever interleaving actually occurs, the resulting state must always be one of
            // the two legitimate members of {VERIFIED, CANONICAL_ENABLED}, never a corrupted value.
            Future<?> enableAttempt = pool.submit(() -> {
                try {
                    resolver.enableCanonical(businessId);
                } catch (ApiException ignored) {
                }
            });
            Future<?> revertAttempt = pool.submit(() -> {
                try {
                    resolver.revertToVerified(businessId);
                } catch (ApiException ignored) {
                }
            });
            enableAttempt.get(30, TimeUnit.SECONDS);
            revertAttempt.get(30, TimeUnit.SECONDS);

            BookingCutoverState finalState = resolver.resolve(businessId);
            assertTrue(finalState == BookingCutoverState.VERIFIED || finalState == BookingCutoverState.CANONICAL_ENABLED,
                    "final state must be a legitimate lifecycle member, got " + finalState);
        } finally {
            pool.shutdownNow();
            businessRepository.deleteById(businessId);
        }
    }
}
