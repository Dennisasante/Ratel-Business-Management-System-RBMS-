package com.ratel.rbms.service;

import com.ratel.rbms.dto.ServiceCatalogItemRequest;
import com.ratel.rbms.dto.ServiceCatalogItemResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5A — proves, with real threads and real separate PostgreSQL
 * connections, that two concurrent edits to the SAME legacy item's price never leave the legacy
 * row and its linked canonical Offering in disagreement. Deliberately NOT @Transactional at the
 * test level — same reasoning as Phase4ConcurrencyTest: a rollback-wrapping test transaction
 * would hide the fixture from the worker threads' own connections entirely.
 *
 * <p>The invariant proven is NOT "which price wins" (a genuine race, either outcome is
 * legitimate) — it is that whichever price wins, the legacy row and its Offering agree on it
 * exactly. Ordinary Postgres row-level locking on the UPDATE statement already serializes the two
 * attempts; this test proves that in practice rather than assuming it.
 */
@SpringBootTest
class Phase5AConcurrencyTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private com.ratel.rbms.repository.OfferingBookingConfigRepository offeringBookingConfigRepository;
    @Autowired private ServiceCatalogService serviceCatalogService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void concurrentPriceUpdatesToTheSameItemNeverLeaveLegacyAndOfferingInDisagreement() throws Exception {
        Business business = businessRepository.save(Business.builder()
                .name("Phase 5A Concurrency Test Business " + UUID.randomUUID().toString().substring(0, 8))
                .slug("phase5a-concurrency-" + UUID.randomUUID().toString().substring(0, 8))
                .industry(Industry.OTHER).currency("GHS").build());
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Haircut").build());

        TenantContext.setBusinessId(business.getId());
        ServiceCatalogItemResponse created = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Race Cut", new BigDecimal("50.00"), false, 30, 1, false, null));
        UUID itemId = created.id();
        TenantContext.clear(); // each worker thread sets its own, below

        // Unlike Phase 4's PolicyVersion, nothing here is append-only/undeletable — clean up
        // fully so this test leaves zero residual local-dev data, not just a documented one.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> {
                TenantContext.setBusinessId(business.getId());
                try {
                    serviceCatalogService.update(itemId,
                            new ServiceCatalogItemRequest(type.getId(), "Race Cut", new BigDecimal("60.00"), false, 30, 1, false, null));
                } finally {
                    TenantContext.clear();
                }
            });
            Future<?> b = pool.submit(() -> {
                TenantContext.setBusinessId(business.getId());
                try {
                    serviceCatalogService.update(itemId,
                            new ServiceCatalogItemRequest(type.getId(), "Race Cut", new BigDecimal("70.00"), false, 30, 1, false, null));
                } finally {
                    TenantContext.clear();
                }
            });
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);

            ServiceCatalogItem legacyAfter = serviceCatalogItemRepository.findById(itemId).orElseThrow();
            Offering offeringAfter = offeringRepository.findByBusinessIdAndLegacyServiceCatalogId(business.getId(), itemId).orElseThrow();

            assertTrue(List.of(new BigDecimal("60.00"), new BigDecimal("70.00")).stream()
                            .anyMatch(p -> p.compareTo(legacyAfter.getPrice()) == 0),
                    "the legacy price must be one of the two attempted values, not some corrupted third value");
            assertEquals(0, legacyAfter.getPrice().compareTo(offeringAfter.getBasePrice()),
                    "whichever price won, the legacy row and its linked Offering must agree exactly — no drift");

            offeringBookingConfigRepository.deleteById(offeringAfter.getId());
            offeringRepository.deleteById(offeringAfter.getId());
            serviceCatalogItemRepository.deleteById(itemId);
            serviceTypeRepository.deleteById(type.getId());
            businessRepository.deleteById(business.getId());
        } finally {
            pool.shutdownNow();
        }
    }
}
