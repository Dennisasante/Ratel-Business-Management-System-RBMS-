package com.ratel.rbms.service;

import com.ratel.rbms.dto.ServiceCatalogItemRequest;
import com.ratel.rbms.dto.ServiceCatalogItemResponse;
import com.ratel.rbms.entity.*;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.repository.*;
import com.ratel.rbms.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5A — final verification gate before close. Proves, with real
 * PostgreSQL and (where required) genuinely independent connections/transactions, the specific
 * properties requested in the pre-close verification pass: explicit row-lock serialization
 * (not just eventual final-value equality), create/update atomicity using a genuine downstream
 * failure point, backfill idempotency across repeated dry/real runs, and
 * OfferingResolutionService's tenant/type correctness. Deliberately NOT @Transactional at the
 * class level — several tests need genuinely separate transactions/connections; each test cleans
 * up its own fixture explicitly (see each test's own final lines).
 */
@SpringBootTest
class Phase5AFinalVerificationTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private ServicePackageRepository servicePackageRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private OfferingBookingConfigRepository offeringBookingConfigRepository;
    @Autowired private ServiceCatalogService serviceCatalogService;
    @Autowired private OfferingResolutionService offeringResolutionService;
    @Autowired private OfferingBackfillService offeringBackfillService;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager entityManager;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private Business newBusiness(String label) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return businessRepository.save(Business.builder()
                .name("Phase 5A Final Verify " + label + " " + unique).slug("phase5a-verify-" + label.toLowerCase() + "-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
    }

    private ServiceType newServiceType(UUID businessId) {
        return serviceTypeRepository.save(ServiceType.builder().businessId(businessId).name("Haircut").build());
    }

    private void deleteFixture(UUID businessId, UUID... catalogItemIds) {
        for (UUID id : catalogItemIds) {
            offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(businessId, id)
                    .ifPresent(o -> {
                        offeringBookingConfigRepository.deleteById(o.getId());
                        offeringRepository.deleteById(o.getId());
                    });
            serviceCatalogItemRepository.deleteById(id);
        }
        serviceTypeRepository.findAll().stream().filter(t -> businessId.equals(t.getBusinessId()))
                .forEach(t -> serviceTypeRepository.deleteById(t.getId()));
        businessRepository.deleteById(businessId);
    }

    // ==================== 1. Concurrency semantics — explicit transactional serialization ====================

    @Test
    void threadAHoldsTheRowLockOpenUntilCommit_threadBBlocksUntilThenAndFinalStateAgrees() throws Exception {
        Business business = newBusiness("Concurrency");
        ServiceType type = newServiceType(business.getId());
        TenantContext.setBusinessId(business.getId());
        ServiceCatalogItemResponse created = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Lock Test Cut", new BigDecimal("50.00"), false, 30, 1, false, null));
        UUID itemId = created.id();
        TenantContext.clear();

        CountDownLatch aWroteNotCommitted = new CountDownLatch(1);
        CountDownLatch releaseACommit = new CountDownLatch(1);
        AtomicReference<Instant> aCommitTime = new AtomicReference<>();
        AtomicReference<Instant> bCompletionTime = new AtomicReference<>();
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        Thread threadA = new Thread(() -> {
            try {
                txTemplate.executeWithoutResult(status -> {
                    TenantContext.setBusinessId(business.getId());
                    serviceCatalogService.update(itemId,
                            new ServiceCatalogItemRequest(type.getId(), "Lock Test Cut", new BigDecimal("60.00"), false, 30, 1, false, null));
                    // Force the UPDATE statements to actually reach Postgres NOW (acquiring the
                    // real row lock) rather than staying deferred in Hibernate's write-behind
                    // buffer until this transaction eventually commits.
                    entityManager.flush();
                    aWroteNotCommitted.countDown();
                    try {
                        assertTrue(releaseACommit.await(30, TimeUnit.SECONDS), "test main thread never released the commit latch");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    TenantContext.clear();
                });
                aCommitTime.set(Instant.now()); // recorded the instant after TransactionTemplate's own commit returns
            } catch (Throwable t) {
                threadFailure.set(t);
            }
        }, "phase5a-thread-A");

        Thread threadB = new Thread(() -> {
            try {
                assertTrue(aWroteNotCommitted.await(30, TimeUnit.SECONDS), "A never reached its pre-commit pause");
                TenantContext.setBusinessId(business.getId());
                // This call's own internal UPDATE statement must block at the Postgres level
                // until A's transaction releases the row lock (commit or rollback) — real,
                // unmodified ServiceCatalogService.update(), no test-only hook.
                serviceCatalogService.update(itemId,
                        new ServiceCatalogItemRequest(type.getId(), "Lock Test Cut", new BigDecimal("70.00"), false, 30, 1, false, null));
                bCompletionTime.set(Instant.now());
                TenantContext.clear();
            } catch (Throwable t) {
                threadFailure.set(t);
            }
        }, "phase5a-thread-B");

        threadA.start();
        assertTrue(aWroteNotCommitted.await(30, TimeUnit.SECONDS));
        threadB.start();

        // B must still be blocked, real proof of row-level serialization — not eventual
        // consistency, an actual observed absence of progress while A holds the lock uncommitted.
        Thread.sleep(800);
        assertNull(threadFailure.get(), "no thread may fail unexpectedly: " + threadFailure.get());
        assertNull(bCompletionTime.get(), "B must not have completed yet — it should still be blocked on A's held row lock");

        releaseACommit.countDown();
        threadA.join(30_000);
        threadB.join(30_000);

        assertNull(threadFailure.get(), "no thread may fail unexpectedly: " + threadFailure.get());
        assertNotNull(aCommitTime.get());
        assertNotNull(bCompletionTime.get(), "B must complete once A's lock is released");
        assertFalse(bCompletionTime.get().isBefore(aCommitTime.get()),
                "B's completion must not precede A's commit — proves real serialization, not a race that happened to end up consistent");

        // Final committed state: exactly one value, and legacy/Offering agree on it exactly —
        // never a partially-synchronized state (e.g. legacy=70 but Offering=60, or vice versa).
        ServiceCatalogItem legacyAfter = serviceCatalogItemRepository.findById(itemId).orElseThrow();
        Offering offeringAfter = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), itemId).orElseThrow();
        assertTrue(List.of(new BigDecimal("60.00"), new BigDecimal("70.00")).stream()
                        .anyMatch(p -> p.compareTo(legacyAfter.getPrice()) == 0),
                "final price must be exactly one of the two attempted values");
        assertEquals(0, legacyAfter.getPrice().compareTo(offeringAfter.getBasePrice()),
                "a committed legacy row must never disagree with its committed Offering — no partially-synchronized committed state can exist");

        deleteFixture(business.getId(), itemId);
    }

    // ==================== 2. ServiceCatalogService.create() atomicity ====================

    @Test
    void createCommitsAllThreeRowsTogether() {
        Business business = newBusiness("CreateHappy");
        ServiceType type = newServiceType(business.getId());
        TenantContext.setBusinessId(business.getId());

        ServiceCatalogItemResponse created = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Happy Path Cut", new BigDecimal("45.00"), true, 30, 2, false, "DEPOSIT"));

        assertTrue(serviceCatalogItemRepository.findById(created.id()).isPresent(), "legacy row must exist");
        Offering offering = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), created.id())
                .orElseThrow(() -> new AssertionError("Offering must exist"));
        assertTrue(offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offering.getId(), business.getId()).isPresent(),
                "OfferingBookingConfig must exist");

        deleteFixture(business.getId(), created.id());
    }

    @Test
    void createRollsBackAllThreeRepresentationsOnAGenuineDownstreamFailure() {
        // ServiceCatalogService.create() has no natural post-sync validation branch the way
        // ServicePackageService.create() has via saveItems() — there is no second item/collection
        // to validate. The genuine failure point used here is real, not test-only: calling the
        // service directly (bypassing the controller) also bypasses jakarta.validation's bean
        // constraints (@Min(5) on durationMinutes) — exactly the same already-documented gap
        // AiToolService.createBooking() itself works around by validating manually. A
        // durationMinutes of 0 passed this way is accepted by ServiceCatalogItem (which has no
        // DB-level check on the column at all) but is rejected by the REAL, pre-existing DB
        // constraint chk_offering_booking_config_duration_positive the moment
        // OfferingSyncService.syncServiceCatalogItem tries to create its OfferingBookingConfig —
        // a genuine constraint violation inside real, unmodified application code, not an
        // artificial throw.
        Business business = newBusiness("CreateFail");
        ServiceType type = newServiceType(business.getId());
        TenantContext.setBusinessId(business.getId());

        ServiceCatalogItemRequest invalidReq = new ServiceCatalogItemRequest(
                type.getId(), "Doomed Duration Cut", new BigDecimal("45.00"), false, 0, 1, false, null);

        DataAccessException ex = assertThrows(DataAccessException.class, () -> serviceCatalogService.create(invalidReq));
        assertNotNull(ex);

        List<ServiceCatalogItem> itemsAfter = serviceCatalogItemRepository.findAllByBusinessIdOrderByNameAsc(business.getId());
        assertTrue(itemsAfter.isEmpty(), "the ServiceCatalogItem row must not survive — flushed then rolled back with the failed sync");
        long offeringsAfter = offeringRepository.findAll().stream().filter(o -> business.getId().equals(o.getBusinessId())).count();
        assertEquals(0, offeringsAfter, "no Offering may survive either");
        long configsAfter = offeringBookingConfigRepository.findAll().stream().filter(c -> business.getId().equals(c.getBusinessId())).count();
        assertEquals(0, configsAfter, "no OfferingBookingConfig may survive either");

        serviceTypeRepository.deleteById(type.getId());
        businessRepository.deleteById(business.getId());
    }

    // ==================== 3. ServiceCatalogService.update() atomicity ====================

    @Test
    void updateLeavesEveryRepresentationUnchangedOnAGenuineDownstreamFailure() {
        Business business = newBusiness("UpdateFail");
        ServiceType type = newServiceType(business.getId());
        TenantContext.setBusinessId(business.getId());

        ServiceCatalogItemResponse created = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Stable Cut", new BigDecimal("50.00"), true, 40, 3, true, "FULL"));
        Offering offeringBefore = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), created.id()).orElseThrow();
        OfferingBookingConfig configBefore = offeringBookingConfigRepository
                .findByOfferingIdAndBusinessId(offeringBefore.getId(), business.getId()).orElseThrow();
        // Snapshot every field this update attempt will try (and fail) to change.
        String nameBefore = offeringBefore.getName();
        BigDecimal priceBefore = offeringBefore.getBasePrice();
        int durationBefore = configBefore.getDurationMinutes();
        int capacityBefore = configBefore.getMaxConcurrentBookings();
        String legacyNameBefore = created.name();
        BigDecimal legacyPriceBefore = created.price();

        // Same genuine failure mechanism as the create() test — an invalid duration that bypasses
        // bean validation by calling the service directly, caught only by the real DB CHECK
        // constraint inside the real sync call this update() triggers.
        ServiceCatalogItemRequest invalidUpdate = new ServiceCatalogItemRequest(
                type.getId(), "Attempted New Name", new BigDecimal("999.00"), false, 0, 9, false, "NONE");

        assertThrows(DataAccessException.class, () -> serviceCatalogService.update(created.id(), invalidUpdate));

        ServiceCatalogItem legacyAfter = serviceCatalogItemRepository.findById(created.id()).orElseThrow();
        Offering offeringAfter = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), created.id()).orElseThrow();
        OfferingBookingConfig configAfter = offeringBookingConfigRepository
                .findByOfferingIdAndBusinessId(offeringAfter.getId(), business.getId()).orElseThrow();

        assertEquals(legacyNameBefore, legacyAfter.getName(), "legacy name must be unchanged after rollback");
        assertEquals(0, legacyPriceBefore.compareTo(legacyAfter.getPrice()), "legacy price must be unchanged after rollback");
        assertEquals(nameBefore, offeringAfter.getName(), "Offering name must be unchanged after rollback");
        assertEquals(0, priceBefore.compareTo(offeringAfter.getBasePrice()), "Offering price must be unchanged after rollback");
        assertEquals(durationBefore, configAfter.getDurationMinutes(), "config duration must be unchanged after rollback");
        assertEquals(capacityBefore, configAfter.getMaxConcurrentBookings(), "config capacity must be unchanged after rollback");

        deleteFixture(business.getId(), created.id());
    }

    // ==================== 5. Backfill idempotency — dry run twice, real run twice ====================

    @Test
    void dryRunTwiceIsPureAndNeverWrites() {
        Business business = newBusiness("DryTwice");
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(business.getId()).serviceTypeId(type.getId()).name("Dry Run Item")
                .price(new BigDecimal("30.00")).active(true).build());

        OfferingBackfillService.BackfillResult first = offeringBackfillService.backfillBusiness(business.getId(), true);
        OfferingBackfillService.BackfillResult second = offeringBackfillService.backfillBusiness(business.getId(), true);

        assertEquals(1, first.serviceCatalogItemsMapped().size());
        assertEquals(1, second.serviceCatalogItemsMapped().size(), "a second dry run must report identically — nothing was written by the first");
        assertTrue(offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.getId()).isEmpty(),
                "two dry runs combined must still have written nothing");

        deleteFixture(business.getId(), item.getId());
    }

    @Test
    void realRunTwiceNeverCreatesDuplicatesAndExactRowCountsHold() {
        Business business = newBusiness("RealTwice");
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(business.getId()).serviceTypeId(type.getId()).name("Real Run Item")
                .price(new BigDecimal("30.00")).active(true).build());

        long offeringCountBefore = countOfferingsFor(business.getId());
        assertEquals(0, offeringCountBefore);

        OfferingBackfillService.BackfillResult firstRun = offeringBackfillService.backfillBusiness(business.getId(), false);
        assertEquals(1, firstRun.serviceCatalogItemsMapped().size());
        assertEquals(0, firstRun.serviceCatalogItemsAlreadyMapped().size());
        long afterFirst = countOfferingsFor(business.getId());
        assertEquals(1, afterFirst, "exactly one Offering must exist after the first real run");

        Offering mappedAfterFirst = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.getId()).orElseThrow();

        OfferingBackfillService.BackfillResult secondRun = offeringBackfillService.backfillBusiness(business.getId(), false);
        assertEquals(0, secondRun.serviceCatalogItemsMapped().size(), "the second real run must map nothing new");
        assertEquals(1, secondRun.serviceCatalogItemsAlreadyMapped().size());
        long afterSecond = countOfferingsFor(business.getId());
        assertEquals(1, afterSecond, "the second real run must not create a duplicate Offering — exact same count as after the first");

        Offering mappedAfterSecond = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), item.getId()).orElseThrow();
        assertEquals(mappedAfterFirst.getId(), mappedAfterSecond.getId(), "the already-mapped record must never be replaced with a different Offering row");

        deleteFixture(business.getId(), item.getId());
    }

    private long countOfferingsFor(UUID businessId) {
        return offeringRepository.findAll().stream().filter(o -> businessId.equals(o.getBusinessId())).count();
    }

    // ==================== 6. OfferingResolutionService correctness and tenant isolation ====================

    @Test
    void resolutionCorrectBusinessCorrectTypeCorrectId_returnsTheOfferingId() {
        Business business = newBusiness("ResolveOK");
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(business.getId()).serviceTypeId(type.getId()).name("Resolve Me").price(new BigDecimal("20.00")).build());
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("SERVICE").name("Resolve Me").legacyServiceCatalogId(item.getId()).build());

        UUID resolved = offeringResolutionService.resolveOfferingId(
                business.getId(), OfferingResolutionService.LegacyType.SERVICE_CATALOG_ITEM, item.getId());
        assertEquals(offering.getId(), resolved);

        deleteFixture(business.getId(), item.getId());
    }

    @Test
    void resolutionWrongBusiness_returnsNullNeverAnotherBusinesssOffering() {
        Business owner = newBusiness("ResolveOwner");
        Business intruder = newBusiness("ResolveIntruder");
        ServiceType type = newServiceType(owner.getId());
        ServiceCatalogItem item = serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(owner.getId()).serviceTypeId(type.getId()).name("Owner's Item").price(new BigDecimal("20.00")).build());
        offeringRepository.save(Offering.builder()
                .businessId(owner.getId()).type("SERVICE").name("Owner's Item").legacyServiceCatalogId(item.getId()).build());

        UUID resolvedByIntruder = offeringResolutionService.resolveOfferingId(
                intruder.getId(), OfferingResolutionService.LegacyType.SERVICE_CATALOG_ITEM, item.getId());
        assertNull(resolvedByIntruder, "resolving another business's legacy item id under a different businessId must never leak that Offering");

        deleteFixture(owner.getId(), item.getId());
        businessRepository.deleteById(intruder.getId());
    }

    @Test
    void resolutionWrongLegacyType_neverCrossResolves() {
        // The same UUID value used as a SERVICE_CATALOG_ITEM id must not accidentally resolve
        // when asked for as a SERVICE_PACKAGE id (or vice versa) — proves resolution is scoped by
        // (businessId, legacyType, legacyId) exactly, never just legacyId alone.
        Business business = newBusiness("ResolveType");
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(business.getId()).serviceTypeId(type.getId()).name("Type Test Item").price(new BigDecimal("20.00")).build());
        offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("SERVICE").name("Type Test Item").legacyServiceCatalogId(item.getId()).build());

        UUID resolvedAsPackage = offeringResolutionService.resolveOfferingId(
                business.getId(), OfferingResolutionService.LegacyType.SERVICE_PACKAGE, item.getId());
        assertNull(resolvedAsPackage, "the same id mapped as a SERVICE_CATALOG_ITEM must never resolve when asked for as a SERVICE_PACKAGE");

        deleteFixture(business.getId(), item.getId());
    }

    @Test
    void resolutionUnmappedRecord_returnsNull_neverCreatesAnOffering() {
        Business business = newBusiness("ResolveUnmapped");
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(business.getId()).serviceTypeId(type.getId()).name("Never Synced").price(new BigDecimal("20.00")).build());

        long before = offeringRepository.count();
        UUID resolved = offeringResolutionService.resolveOfferingId(
                business.getId(), OfferingResolutionService.LegacyType.SERVICE_CATALOG_ITEM, item.getId());
        long after = offeringRepository.count();

        assertNull(resolved);
        assertEquals(before, after, "resolveOfferingId must never implicitly create an Offering as a side effect");

        deleteFixture(business.getId(), item.getId());
    }
}
