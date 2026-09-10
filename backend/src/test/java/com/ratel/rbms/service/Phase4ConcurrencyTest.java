package com.ratel.rbms.service;

import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Policy;
import com.ratel.rbms.entity.PolicyCommitment;
import com.ratel.rbms.entity.PolicyOverride;
import com.ratel.rbms.entity.PolicyVersion;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.PolicyCommitmentRepository;
import com.ratel.rbms.repository.PolicyOverrideRepository;
import com.ratel.rbms.repository.PolicyRepository;
import com.ratel.rbms.repository.PolicyVersionRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 4 — proves the frozen concurrency design (Revision 4 §2/§10)
 * against real PostgreSQL with REAL, separate threads/connections racing the SAME row.
 *
 * Deliberately NOT @Transactional: a rollback-wrapping test transaction would hide every fixture
 * row from worker threads' own connections (they'd see nothing to lock), which would prove
 * nothing about real concurrent behaviour. Fixtures are therefore genuinely committed, and are
 * cleaned up explicitly in @AfterAll — except the one Policy/PolicyVersion (and the Business that
 * owns it) shared across all tests here, which the schema itself makes permanently undeletable by
 * design (policy_versions' own append-only triggers reject every DELETE, and policies/businesses
 * above it are transitively blocked by ON DELETE RESTRICT) — this is the exact audit-evidence
 * guarantee Phase 3 was built to provide, not a cleanup oversight. See the Phase 4 implementation
 * report for the honest accounting of what this leaves behind in the LOCAL DEV database only.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase4ConcurrencyTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private PolicyVersionRepository policyVersionRepository;
    @Autowired private PolicyCommitmentRepository policyCommitmentRepository;
    @Autowired private PolicyOverrideRepository policyOverrideRepository;
    @Autowired private PolicyEngine policyEngine;

    private static final String ACTION = "SALE_CREATE";

    private UUID sharedBusinessId;
    private UUID sharedPolicyId;
    private UUID sharedVersionId;

    @BeforeAll
    void setUpSharedFixture() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Business business = businessRepository.save(Business.builder()
                .name("Phase 4 Concurrency Test Business " + unique).slug("phase4-concurrency-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
        sharedBusinessId = business.getId();

        Policy policy = policyRepository.save(Policy.builder()
                .businessId(sharedBusinessId).policyKey("concurrency-policy-" + unique)
                .appliesToAction(ACTION).createdBy(UUID.randomUUID()).build());
        sharedPolicyId = policy.getId();

        PolicyVersion version = policyVersionRepository.save(PolicyVersion.builder()
                .businessId(sharedBusinessId).policyId(sharedPolicyId).versionNumber(1)
                .title("V1").content("Shared concurrency-test policy version.")
                .requiresAcknowledgement(true).blocksTransaction(true).staffOverridable(true)
                .createdBy(UUID.randomUUID()).build());
        sharedVersionId = version.getId();
    }

    @AfterAll
    void tearDownWhatTheSchemaAllows() {
        // Everything spawned per-test (commitments/overrides) is cleaned up inside each test's
        // own try/finally. The shared business/policy/version above is left in place — the schema
        // itself refuses to let it be removed (see class-level note).
    }

    // ==================== Commitment completion — real double-completion race ====================

    @Test
    void concurrentLinkCommitmentToTransaction_exactlyOneOfTwentySucceeds() throws InterruptedException {
        UUID commitment = policyEngine.beginCommitment(sharedBusinessId, "SALE");
        int attempts = 20;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        AtomicInteger otherFailures = new AtomicInteger();

        try {
            List<Runnable> tasks = java.util.stream.IntStream.range(0, attempts)
                    .<Runnable>mapToObj(i -> () -> {
                        try {
                            policyEngine.linkCommitmentToTransaction(sharedBusinessId, commitment, "SALE", UUID.randomUUID());
                            succeeded.incrementAndGet();
                        } catch (ApiException e) {
                            if (e.getStatus() == org.springframework.http.HttpStatus.CONFLICT) {
                                conflicted.incrementAndGet();
                            } else {
                                otherFailures.incrementAndGet();
                            }
                        }
                    })
                    .toList();
            List<java.util.concurrent.Future<?>> futures = tasks.stream().map(pool::submit).toList();
            for (var f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    fail("Worker thread failed unexpectedly: " + e);
                }
            }

            assertEquals(1, succeeded.get(), "exactly one of twenty concurrent completions of the SAME commitment must succeed");
            assertEquals(attempts - 1, conflicted.get(), "every other concurrent attempt must be rejected as a real CONFLICT, not silently overwrite the winner");
            assertEquals(0, otherFailures.get());

            PolicyCommitment stored = policyCommitmentRepository.findByCommitmentReferenceAndBusinessId(commitment, sharedBusinessId).orElseThrow();
            assertEquals("COMPLETED", stored.getStatus());
        } finally {
            pool.shutdownNow();
            policyCommitmentRepository.deleteById(commitment);
        }
    }

    // ==================== transaction_type binding — real race between two different types ====================

    @Test
    void concurrentEvaluateGateWithDifferentTransactionTypes_exactlyOneBinds() throws InterruptedException {
        UUID commitment = policyEngine.beginCommitment(sharedBusinessId); // unbound

        try {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                var bookingFuture = pool.submit(() ->
                        policyEngine.evaluateGate(sharedBusinessId, ACTION, null, commitment, "BOOKING", null));
                var saleFuture = pool.submit(() ->
                        policyEngine.evaluateGate(sharedBusinessId, ACTION, null, commitment, "SALE", null));

                GateResult bookingResult = bookingFuture.get(30, TimeUnit.SECONDS);
                GateResult saleResult = saleFuture.get(30, TimeUnit.SECONDS);

                // Both will actually BLOCK here (the shared policy requires ack, which was never
                // recorded) — that's fine, this test only cares about which type got bound, not
                // whether the transaction could ultimately proceed. Exactly one of the two calls'
                // requested type must be the one that ends up stored — never a value neither call
                // requested (which would mean the row was corrupted by an unsynchronized race).
                PolicyCommitment stored = policyCommitmentRepository.findByCommitmentReferenceAndBusinessId(commitment, sharedBusinessId).orElseThrow();
                assertTrue(stored.getTransactionType().equals("BOOKING") || stored.getTransactionType().equals("SALE"));

                boolean bookingWasBoundType = "BOOKING".equals(stored.getTransactionType());
                if (bookingWasBoundType) {
                    assertFalse(bookingResult.reasons().stream().anyMatch(r -> r.contains("different transaction type")),
                            "the call whose type actually got bound must never itself report a type mismatch");
                    assertTrue(saleResult.reasons().stream().anyMatch(r -> r.contains("different transaction type")),
                            "the losing call must observe the mismatch, not silently rebind");
                } else {
                    assertTrue(bookingResult.reasons().stream().anyMatch(r -> r.contains("different transaction type")));
                    assertFalse(saleResult.reasons().stream().anyMatch(r -> r.contains("different transaction type")));
                }
            } catch (Exception e) {
                fail("Worker thread failed unexpectedly: " + e);
            } finally {
                pool.shutdownNow();
            }
        } finally {
            policyCommitmentRepository.deleteById(commitment);
        }
    }

    // ==================== Override consumption — real race, single-use proven under load ====================

    @Test
    void concurrentEvaluateGateAgainstTheSameApprovedOverride_exactlyOneConsumesIt() throws Exception {
        UUID commitment = policyEngine.beginCommitment(sharedBusinessId, "SALE");
        UUID overrideId = policyEngine.requestOverride(sharedBusinessId, commitment, sharedPolicyId, sharedVersionId,
                UUID.randomUUID(), "Concurrency test override.", true); // owner => auto-APPROVED

        try {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                var first = pool.submit(() -> policyEngine.evaluateGate(sharedBusinessId, ACTION, null, commitment, "SALE", null));
                var second = pool.submit(() -> policyEngine.evaluateGate(sharedBusinessId, ACTION, null, commitment, "SALE", null));

                GateResult r1 = first.get(30, TimeUnit.SECONDS);
                GateResult r2 = second.get(30, TimeUnit.SECONDS);

                long allowedCount = List.of(r1, r2).stream().filter(GateResult::allowed).count();
                assertEquals(1, allowedCount, "a single-use APPROVED override must let exactly one of two concurrent attempts through, never both");

                PolicyOverride stored = policyOverrideRepository.findByIdAndBusinessId(overrideId, sharedBusinessId).orElseThrow();
                assertEquals("CONSUMED", stored.getStatus());
            } finally {
                pool.shutdownNow();
            }
        } finally {
            policyOverrideRepository.deleteById(overrideId);
            policyCommitmentRepository.deleteById(commitment);
        }
    }

    // ==================== Override approval — real race, exactly one decision wins ====================

    @Test
    void concurrentApproveOnTheSamePendingOverride_exactlyOneOfTenSucceeds() throws InterruptedException {
        UUID commitment = policyEngine.beginCommitment(sharedBusinessId, "SALE");
        UUID overrideId = policyEngine.requestOverride(sharedBusinessId, commitment, sharedPolicyId, sharedVersionId,
                UUID.randomUUID(), "Concurrency test approval race.", false); // non-owner => PENDING

        int attempts = 10;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();

        try {
            List<java.util.concurrent.Future<?>> futures = java.util.stream.IntStream.range(0, attempts)
                    .<Runnable>mapToObj(i -> () -> {
                        try {
                            policyEngine.approveOverride(sharedBusinessId, overrideId, UUID.randomUUID(), "Approve attempt " + i);
                            succeeded.incrementAndGet();
                        } catch (ApiException e) {
                            conflicted.incrementAndGet();
                        }
                    })
                    .map(pool::submit)
                    .toList();
            for (var f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    fail("Worker thread failed unexpectedly: " + e);
                }
            }

            assertEquals(1, succeeded.get(), "exactly one of ten concurrent approve attempts on the SAME PENDING override must succeed");
            assertEquals(attempts - 1, conflicted.get());
        } finally {
            pool.shutdownNow();
            policyOverrideRepository.deleteById(overrideId);
            policyCommitmentRepository.deleteById(commitment);
        }
    }
}
