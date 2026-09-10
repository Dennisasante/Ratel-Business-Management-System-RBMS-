package com.ratel.rbms.service;

import com.ratel.rbms.dto.SaleItemRequest;
import com.ratel.rbms.dto.SaleRequest;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.Policy;
import com.ratel.rbms.entity.PolicyCommitment;
import com.ratel.rbms.entity.PolicyDisclosure;
import com.ratel.rbms.entity.PolicyOverride;
import com.ratel.rbms.entity.PolicyVersion;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.entity.enums.PaymentMethod;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.*;
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
 * Talia Unified Platform, Phase 4 — proves, with real PostgreSQL and the actual
 * {@code @Transactional} service boundary (not an analytical argument), that a failure occurring
 * AFTER {@link PolicyEngine#evaluateGate} has consumed a {@link PolicyOverride} but BEFORE the
 * enclosing transaction commits leaves nothing behind: the override reverts to {@code APPROVED},
 * the commitment stays {@code ACTIVE}, and no transaction row or disclosure linkage survives.
 *
 * <p>The failure is triggered by real, unmodified application code — {@code SaleService.createSale}
 * itself throws a genuine {@code ApiException} when an item references a non-existent
 * {@code ServiceCatalogItem}, exactly the same way it would for a real caller — deliberately after
 * the gate has already run and the {@code Sale} row has already been inserted and flushed. Spring's
 * default rollback-on-RuntimeException then rolls back the ENTIRE {@code @Transactional} method: the
 * gate's override consumption, the flushed {@code Sale} insert, everything.
 *
 * <p>Deliberately NOT @Transactional at the test level — see Phase4ConcurrencyTest's own class
 * comment for why: a JUnit-managed wrapping transaction would only ever flag the shared transaction
 * rollback-only without actually rolling back before this test's own assertions run, so a read
 * immediately afterward would still observe the not-yet-reverted, uncommitted state. Here,
 * {@code createSale} owns its own real, top-level transaction; when it throws, Spring performs a
 * genuine rollback and closes it before control returns to this test, so every read below is a
 * fresh, honest observation of what actually persisted.
 */
@SpringBootTest
class Phase4RollbackTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private PolicyVersionRepository policyVersionRepository;
    @Autowired private PolicyCommitmentRepository policyCommitmentRepository;
    @Autowired private PolicyDisclosureRepository policyDisclosureRepository;
    @Autowired private PolicyOverrideRepository policyOverrideRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private PolicyEngine policyEngine;
    @Autowired private SaleService saleService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void failureAfterOverrideConsumptionRollsBackEverything_realDatabaseProof() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Business business = businessRepository.save(Business.builder()
                .name("Phase 4 Rollback Test Business " + unique).slug("phase4-rollback-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());

        Policy policy = policyRepository.save(Policy.builder()
                .businessId(business.getId()).policyKey("rollback-test-" + unique)
                .appliesToAction("SALE_CREATE").createdBy(UUID.randomUUID()).build());
        PolicyVersion version = policyVersionRepository.save(PolicyVersion.builder()
                .businessId(business.getId()).policyId(policy.getId()).versionNumber(1)
                .title("Required").content("Must be shown and agreed to.")
                .requiresAcknowledgement(true).blocksTransaction(true).staffOverridable(true)
                .createdBy(UUID.randomUUID()).build());

        Customer customer = customerRepository.save(Customer.builder()
                .businessId(business.getId()).fullName("Rollback Test Customer").phone("+233200000099").build());
        User cashier = userRepository.save(User.builder()
                .businessId(business.getId()).fullName("Rollback Test Cashier")
                .email("rollback-cashier+" + unique + "@example.com").role(Role.OWNER).build());
        TenantContext.setBusinessId(business.getId());
        TenantContext.setUserId(cashier.getId());
        TenantContext.setRole("OWNER");

        UUID commitment = policyEngine.beginCommitment(business.getId()); // unbound, ACTIVE
        UUID overrideId = policyEngine.requestOverride(business.getId(), commitment, policy.getId(), version.getId(),
                cashier.getId(), "Rollback proof — owner override.", true); // auto-APPROVED

        // A sale item pointing at a ServiceCatalogItem that does not exist — passes bean-shape
        // validation, but SaleService.createSale() will throw NOT_FOUND resolving it, AFTER the
        // gate has already run (consuming the override) and the Sale row has already been
        // inserted+flushed. Real, unmodified application code; nothing test-only about this path.
        SaleRequest req = new SaleRequest(customer.getId(), PaymentMethod.CASH,
                List.of(new SaleItemRequest(null, UUID.randomUUID(), 1, null, null)), commitment);

        ApiException ex = assertThrows(ApiException.class, () -> saleService.createSale(req));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatus(),
                "sanity check: the failure must be the downstream item-resolution error, not the gate itself");

        // ---- Fresh reads, after the real rollback, each its own new Spring Data transaction ----

        PolicyOverride overrideAfter = policyOverrideRepository.findByIdAndBusinessId(overrideId, business.getId()).orElseThrow();
        assertEquals("APPROVED", overrideAfter.getStatus(), "the override must revert to APPROVED, not remain CONSUMED");
        assertNull(overrideAfter.getConsumedAt(), "consumed_at must revert to null");

        PolicyCommitment commitmentAfter = policyCommitmentRepository.findByCommitmentReferenceAndBusinessId(commitment, business.getId()).orElseThrow();
        assertEquals("ACTIVE", commitmentAfter.getStatus(), "the commitment must remain ACTIVE, never COMPLETED");
        assertNull(commitmentAfter.getCompletedAt());
        assertNull(commitmentAfter.getTransactionType(),
                "even the transaction_type bind performed inside the same failed attempt must be rolled back");

        List<Sale> salesAfter = saleRepository.findAllByBusinessIdAndCustomerId(business.getId(), customer.getId());
        assertTrue(salesAfter.isEmpty(), "no Sale row may survive — the flushed-but-uncommitted insert must be rolled back");

        List<PolicyDisclosure> disclosuresAfter = policyDisclosureRepository
                .findAllByBusinessIdAndPolicyIdAndPolicyVersionIdAndCommitmentReference(business.getId(), policy.getId(), version.getId(), commitment);
        assertTrue(disclosuresAfter.isEmpty(),
                "no disclosure/transaction linkage may exist either — this attempt was satisfied purely via the "
                        + "override, so none was ever created, and the failed attempt must not have manufactured one");

        // Cleanup: commitments/overrides are safely deletable; the Policy/PolicyVersion/Business
        // themselves are not (the schema's own append-only guarantee — see Phase4ConcurrencyTest's
        // class comment for the same, already-documented reasoning).
        policyOverrideRepository.deleteById(overrideId);
        policyCommitmentRepository.deleteById(commitment);
    }
}
