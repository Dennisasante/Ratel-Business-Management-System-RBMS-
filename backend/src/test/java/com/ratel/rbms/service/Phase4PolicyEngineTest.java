package com.ratel.rbms.service;

import com.ratel.rbms.entity.*;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 4 — proves the frozen design (Revision 4 + Final Corrections)
 * against real PostgreSQL: the authoritative gate, customer-identity evidence rule, version-aware
 * satisfaction, commitment lifecycle/terminal-reuse rejection, staff-override lifecycle
 * (request/approve/reject/cancel/consume/expire), and policy_overrides tenant isolation.
 * @Transactional rolls every test back; nothing here touches real data permanently.
 */
@SpringBootTest
@Transactional
class Phase4PolicyEngineTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private PolicyVersionRepository policyVersionRepository;
    @Autowired private PolicyCommitmentRepository policyCommitmentRepository;
    @Autowired private PolicyDisclosureRepository policyDisclosureRepository;
    @Autowired private PolicyOverrideRepository policyOverrideRepository;
    @Autowired private PolicyEngine policyEngine;

    private static final String ACTION = "SALE_CREATE";

    private Business newBusiness() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return businessRepository.save(Business.builder()
                .name("Phase 4 Test Business " + unique).slug("phase4-test-business-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
    }

    private Policy newPolicyWithFirstVersion(UUID businessId, boolean requiresAck, boolean blocks, boolean overridable) {
        Policy policy = policyRepository.save(Policy.builder()
                .businessId(businessId).policyKey("policy-" + UUID.randomUUID().toString().substring(0, 8))
                .appliesToAction(ACTION).createdBy(UUID.randomUUID()).build());
        policyVersionRepository.save(PolicyVersion.builder()
                .businessId(businessId).policyId(policy.getId()).versionNumber(1)
                .title("V1").content("V1 content.")
                .requiresAcknowledgement(requiresAck).blocksTransaction(blocks).staffOverridable(overridable)
                .createdBy(UUID.randomUUID()).build());
        return policy;
    }

    private PolicyVersion currentVersion(UUID policyId) {
        return policyVersionRepository.findTopByPolicyIdOrderByVersionNumberDesc(policyId).orElseThrow();
    }

    // ==================== Empty-applicable-set / no-commitment ====================

    @Test
    void gateAllowsWhenNoApplicablePolicyExists_evenWithNoCommitment() {
        Business business = newBusiness();
        GateResult result = policyEngine.evaluateGate(business.getId(), ACTION, null, null, "SALE", null);
        assertTrue(result.allowed(), "a business with zero configured policies must see zero behaviour change");
    }

    @Test
    void gateBlocksWhenApplicablePolicyExistsButNoCommitmentOpened() {
        Business business = newBusiness();
        newPolicyWithFirstVersion(business.getId(), true, true, false);

        GateResult result = policyEngine.evaluateGate(business.getId(), ACTION, null, null, "SALE", null);
        assertFalse(result.allowed());
        assertFalse(result.reasons().isEmpty());
    }

    // ==================== transaction_type binding ====================

    @Test
    void gateBindsTransactionTypeOnFirstUse() {
        // Binding only happens on a gate call that actually has an applicable policy to check —
        // a business with zero applicable policies for this action short-circuits to ALLOW before
        // ever touching the commitment row (see gateAllowsWhenNoApplicablePolicyExists...), so a
        // policy must be configured here to actually exercise the bind path.
        Business business = newBusiness();
        newPolicyWithFirstVersion(business.getId(), false, false, false); // present, but never blocks — just needs applicablePolicies() non-empty
        UUID commitment = policyEngine.beginCommitment(business.getId()); // no type yet

        GateResult result = policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", null);
        assertTrue(result.allowed());
        PolicyCommitment stored = policyCommitmentRepository.findByCommitmentReferenceAndBusinessId(commitment, business.getId()).orElseThrow();
        assertEquals("SALE", stored.getTransactionType());
    }

    @Test
    void gateBlocksOnTransactionTypeMismatch() {
        Business business = newBusiness();
        newPolicyWithFirstVersion(business.getId(), true, true, false);
        UUID commitment = policyEngine.beginCommitment(business.getId(), "BOOKING");

        GateResult result = policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", null);
        assertFalse(result.allowed());
        assertTrue(result.reasons().get(0).contains("different transaction type"));
    }

    // ==================== Full allow flow + commitment completion ====================

    @Test
    void gateAllowsAfterDisclosureAndAcknowledgement_andLinkCompletesTheCommitment() {
        Business business = newBusiness();
        UUID customerId = UUID.randomUUID();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, false);
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");

        assertFalse(policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", customerId).allowed());

        PolicyDisclosure disclosure = policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment,
                customerId, null, "CUSTOMER_SELF_SERVICE", "WEB");
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "CUSTOMER", customerId);

        GateResult result = policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", customerId);
        assertTrue(result.allowed());

        UUID transactionId = UUID.randomUUID();
        policyEngine.linkCommitmentToTransaction(business.getId(), commitment, "SALE", transactionId);

        PolicyCommitment stored = policyCommitmentRepository.findByCommitmentReferenceAndBusinessId(commitment, business.getId()).orElseThrow();
        assertEquals("COMPLETED", stored.getStatus());
        assertNotNull(stored.getCompletedAt());
    }

    @Test
    void terminalCommitmentCannotBeLinkedAgain() {
        Business business = newBusiness();
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");
        policyEngine.linkCommitmentToTransaction(business.getId(), commitment, "SALE", UUID.randomUUID());

        ApiException ex = assertThrows(ApiException.class,
                () -> policyEngine.linkCommitmentToTransaction(business.getId(), commitment, "SALE", UUID.randomUUID()));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void terminalCommitmentIsRejectedByTheGate() {
        Business business = newBusiness();
        newPolicyWithFirstVersion(business.getId(), true, true, false);
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");
        policyEngine.linkCommitmentToTransaction(business.getId(), commitment, "SALE", UUID.randomUUID());

        GateResult result = policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", null);
        assertFalse(result.allowed());
        assertTrue(result.reasons().get(0).contains("no longer active"));
    }

    @Test
    void abandonAndInvalidateAreNoOpsOnAnAlreadyTerminalCommitment() {
        Business business = newBusiness();
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");
        policyEngine.abandonCommitment(business.getId(), commitment);

        PolicyCommitment afterAbandon = policyCommitmentRepository.findByCommitmentReferenceAndBusinessId(commitment, business.getId()).orElseThrow();
        assertEquals("ABANDONED", afterAbandon.getStatus());

        policyEngine.invalidateCommitment(business.getId(), commitment); // must be a silent no-op, not throw
        PolicyCommitment afterInvalidateAttempt = policyCommitmentRepository.findByCommitmentReferenceAndBusinessId(commitment, business.getId()).orElseThrow();
        assertEquals("ABANDONED", afterInvalidateAttempt.getStatus(), "a terminal commitment can never be re-transitioned");
    }

    // ==================== Customer identity (Final Corrections §1) ====================

    @Test
    void nullTransactionCustomerIdBlocksCustomerScopedEvidence() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, false);
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");
        UUID realCustomer = UUID.randomUUID();

        PolicyDisclosure disclosure = policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment,
                realCustomer, null, "CUSTOMER_SELF_SERVICE", "WEB");
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "CUSTOMER", realCustomer);

        GateResult withNullCustomer = policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", null);
        assertFalse(withNullCustomer.allowed(), "CUSTOMER-scoped evidence must never satisfy a transaction with no customer identity");
    }

    @Test
    void mismatchedCustomerIdBlocksEvidence() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, false);
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");
        UUID customerA = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();

        PolicyDisclosure disclosure = policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment,
                customerA, null, "CUSTOMER_SELF_SERVICE", "WEB");
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "CUSTOMER", customerA);

        GateResult forCustomerB = policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", customerB);
        assertFalse(forCustomerB.allowed(), "Customer B must not be able to satisfy policy requirements using Customer A's acknowledgement");
    }

    @Test
    void matchingCustomerIdSatisfies() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, false);
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");
        UUID customer = UUID.randomUUID();

        PolicyDisclosure disclosure = policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment,
                customer, null, "CUSTOMER_SELF_SERVICE", "WEB");
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "CUSTOMER", customer);

        GateResult result = policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", customer);
        assertTrue(result.allowed());
    }

    @Test
    void staffTypeEvidenceIsUnaffectedByTransactionCustomerId() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, false);
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");
        UUID staffId = UUID.randomUUID();

        PolicyDisclosure disclosure = policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment,
                null, null, "STAFF", "IN_PERSON");
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "STAFF", staffId);

        // Neither a null nor some unrelated customerId should matter for STAFF-type evidence.
        assertTrue(policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", null).allowed());
        assertTrue(policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", UUID.randomUUID()).allowed());
    }

    // ==================== Version-aware satisfaction (Revision 4 §2 / Final Corrections) ====================

    @Test
    void oldVersionAcknowledgementDoesNotSatisfyANewerVersion_viaTheGate() {
        Business business = newBusiness();
        UUID customer = UUID.randomUUID();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, false);
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");

        PolicyDisclosure v1Disclosure = policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment,
                customer, null, "CUSTOMER_SELF_SERVICE", "WEB");
        policyEngine.recordAcknowledgement(business.getId(), v1Disclosure.getId(), "CUSTOMER", customer);
        assertTrue(policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", customer).allowed());

        // Policy edited — a real corkage-fee-style change, same worked example as Revision 4 §2.
        policyVersionRepository.save(PolicyVersion.builder()
                .businessId(business.getId()).policyId(policy.getId()).versionNumber(2)
                .title("V2").content("V2 content — materially different.")
                .requiresAcknowledgement(true).blocksTransaction(true)
                .createdBy(UUID.randomUUID()).build());

        GateResult afterEdit = policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", customer);
        assertFalse(afterEdit.allowed(), "v1's acknowledgement must not automatically satisfy v2");
    }

    // ==================== Staff override lifecycle (Revision 4 §9 / Final Corrections §2) ====================

    @Test
    void nonOwnerRequestStartsPending_ownerRequestIsAutoApproved() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, true);
        PolicyVersion version = currentVersion(policy.getId());

        UUID staffCommitment = policyEngine.beginCommitment(business.getId(), "SALE");
        UUID staffOverrideId = policyEngine.requestOverride(business.getId(), staffCommitment, policy.getId(), version.getId(),
                UUID.randomUUID(), "Customer insisted, manager unavailable to confirm live.", false);
        PolicyOverride staffRequested = policyOverrideRepository.findByIdAndBusinessId(staffOverrideId, business.getId()).orElseThrow();
        assertEquals("PENDING", staffRequested.getStatus());
        assertNull(staffRequested.getDecidedBy());

        // Owner's own request self-decides — a real, evidenced row, never silently skipped
        // (deliberate deviation from SaleService.refund()'s isOwner()-skips-the-queue convention).
        // A separate commitment is used deliberately: the unique active-tuple index means a
        // second request for the SAME (commitment, policy, version) while the first is still
        // PENDING would itself be rejected — see duplicateActiveOverrideForTheSameTupleIsRejected.
        UUID ownerCommitment = policyEngine.beginCommitment(business.getId(), "SALE");
        UUID ownerId = UUID.randomUUID();
        UUID ownerOverrideId = policyEngine.requestOverride(business.getId(), ownerCommitment, policy.getId(), version.getId(),
                ownerId, "Owner override.", true);
        PolicyOverride ownerRequested = policyOverrideRepository.findByIdAndBusinessId(ownerOverrideId, business.getId()).orElseThrow();
        assertEquals("APPROVED", ownerRequested.getStatus());
        assertEquals(ownerId, ownerRequested.getDecidedBy());
        assertNotNull(ownerRequested.getDecidedAt());
    }

    @Test
    void ownerRequestedOverrideIsSelfDecidedAndImmediatelyUsableByTheGate() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, true);
        PolicyVersion version = currentVersion(policy.getId());
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");
        UUID ownerId = UUID.randomUUID();

        UUID overrideId = policyEngine.requestOverride(business.getId(), commitment, policy.getId(), version.getId(),
                ownerId, "Owner overriding on the spot.", true);
        PolicyOverride stored = policyOverrideRepository.findByIdAndBusinessId(overrideId, business.getId()).orElseThrow();
        assertEquals("APPROVED", stored.getStatus());
        assertEquals(ownerId, stored.getDecidedBy());

        GateResult result = policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", null);
        assertTrue(result.allowed(), "an APPROVED override for the exact blocking (policy, version) pair must let the gate proceed");

        PolicyOverride afterConsumption = policyOverrideRepository.findByIdAndBusinessId(overrideId, business.getId()).orElseThrow();
        assertEquals("CONSUMED", afterConsumption.getStatus());
        assertNotNull(afterConsumption.getConsumedAt());
    }

    @Test
    void nonOverridablePolicyIgnoresAnApprovedOverride() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, false); // NOT staffOverridable
        PolicyVersion version = currentVersion(policy.getId());
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");

        // Even a well-formed APPROVED override must not help — the version itself doesn't allow it.
        // (requestOverride doesn't check staffOverridable itself — that's the gate's job, matching
        // "PolicyEngine.evaluateGate is the sole authority" boundary.)
        policyEngine.requestOverride(business.getId(), commitment, policy.getId(), version.getId(),
                UUID.randomUUID(), "Attempted override on a non-overridable policy.", true);

        GateResult result = policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", null);
        assertFalse(result.allowed());
    }

    @Test
    void consumedOverrideCannotBeReusedForASecondAttempt() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, true);
        PolicyVersion version = currentVersion(policy.getId());
        UUID commitment1 = policyEngine.beginCommitment(business.getId(), "SALE");

        policyEngine.requestOverride(business.getId(), commitment1, policy.getId(), version.getId(),
                UUID.randomUUID(), "First attempt.", true);
        assertTrue(policyEngine.evaluateGate(business.getId(), ACTION, null, commitment1, "SALE", null).allowed());

        // A second, unrelated commitment attempt against the same policy/version must NOT find
        // any usable override — the consumed one is gone, and no new one was requested for it.
        UUID commitment2 = policyEngine.beginCommitment(business.getId(), "SALE");
        GateResult second = policyEngine.evaluateGate(business.getId(), ACTION, null, commitment2, "SALE", null);
        assertFalse(second.allowed(), "a consumed, single-use override must never satisfy a different commitment attempt");
    }

    @Test
    void approveIsRejectedOnAnAlreadyDecidedOverride_conflictNotSilentOverwrite() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, true);
        PolicyVersion version = currentVersion(policy.getId());
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");
        UUID overrideId = policyEngine.requestOverride(business.getId(), commitment, policy.getId(), version.getId(),
                UUID.randomUUID(), "Needs approval.", false);

        policyEngine.approveOverride(business.getId(), overrideId, UUID.randomUUID(), "Approved.");

        ApiException ex = assertThrows(ApiException.class,
                () -> policyEngine.approveOverride(business.getId(), overrideId, UUID.randomUUID(), "Approved again?"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void rejectedOverrideIsNeverUsableByTheGate() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, true);
        PolicyVersion version = currentVersion(policy.getId());
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");
        UUID overrideId = policyEngine.requestOverride(business.getId(), commitment, policy.getId(), version.getId(),
                UUID.randomUUID(), "Please override.", false);

        policyEngine.rejectOverride(business.getId(), overrideId, UUID.randomUUID(), "Not justified.");

        GateResult result = policyEngine.evaluateGate(business.getId(), ACTION, null, commitment, "SALE", null);
        assertFalse(result.allowed());
    }

    @Test
    void cancelWorksFromPendingAndFromApproved_neverFromTerminal() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, true);
        PolicyVersion version = currentVersion(policy.getId());
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");

        UUID pendingId = policyEngine.requestOverride(business.getId(), commitment, policy.getId(), version.getId(),
                UUID.randomUUID(), "Will cancel from pending.", false);
        policyEngine.cancelOverride(business.getId(), pendingId);
        assertEquals("CANCELLED", policyOverrideRepository.findByIdAndBusinessId(pendingId, business.getId()).orElseThrow().getStatus());

        assertThrows(ApiException.class, () -> policyEngine.cancelOverride(business.getId(), pendingId),
                "cancelling an already-terminal override must be rejected, not silently accepted");

        // Cancel from APPROVED (decided, not yet consumed) — a different commitment, since the
        // unique active-tuple index would otherwise collide with the still-visible CANCELLED row's
        // tuple... actually CANCELLED is not in the partial index's WHERE clause, so reuse is fine,
        // but a fresh commitment keeps this second scenario independent and easy to read.
        UUID commitment2 = policyEngine.beginCommitment(business.getId(), "SALE");
        UUID approvedId = policyEngine.requestOverride(business.getId(), commitment2, policy.getId(), version.getId(),
                UUID.randomUUID(), "Will cancel from approved.", false);
        policyEngine.approveOverride(business.getId(), approvedId, UUID.randomUUID(), "Approved, then reconsidered.");
        assertEquals("APPROVED", policyOverrideRepository.findByIdAndBusinessId(approvedId, business.getId()).orElseThrow().getStatus());

        policyEngine.cancelOverride(business.getId(), approvedId);
        assertEquals("CANCELLED", policyOverrideRepository.findByIdAndBusinessId(approvedId, business.getId()).orElseThrow().getStatus());
    }

    @Test
    void invalidatingACommitmentExpiresItsOutstandingOverrides() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, true);
        PolicyVersion version = currentVersion(policy.getId());
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");

        UUID pendingId = policyEngine.requestOverride(business.getId(), commitment, policy.getId(), version.getId(),
                UUID.randomUUID(), "Outstanding when invalidated.", false);

        policyEngine.invalidateCommitment(business.getId(), commitment);

        PolicyOverride afterInvalidate = policyOverrideRepository.findByIdAndBusinessId(pendingId, business.getId()).orElseThrow();
        assertEquals("EXPIRED", afterInvalidate.getStatus());
    }

    // ==================== policy_overrides tenant isolation (composite FKs) ====================

    @Test
    void policyOverrideCannotReferenceAnotherBusinesssCommitment() {
        Business a = newBusiness();
        Business b = newBusiness();
        Policy policyB = newPolicyWithFirstVersion(b.getId(), true, true, true);
        PolicyVersion versionB = currentVersion(policyB.getId());
        UUID commitmentA = policyEngine.beginCommitment(a.getId(), "SALE");

        PolicyOverride crossBusiness = PolicyOverride.builder()
                .businessId(b.getId()).commitmentReference(commitmentA) // belongs to business A
                .policyId(policyB.getId()).policyVersionId(versionB.getId())
                .requestedBy(UUID.randomUUID()).reason("cross business").build();

        assertThrows(DataIntegrityViolationException.class,
                () -> policyOverrideRepository.saveAndFlush(crossBusiness),
                "fk_policy_overrides_commitment must reject a commitment registered to a different business");
    }

    @Test
    void policyOverrideCannotReferenceAnotherBusinesssPolicy() {
        Business a = newBusiness();
        Business b = newBusiness();
        Policy policyA = newPolicyWithFirstVersion(a.getId(), true, true, true);
        PolicyVersion versionA = currentVersion(policyA.getId());
        UUID commitmentB = policyEngine.beginCommitment(b.getId(), "SALE");

        PolicyOverride crossBusiness = PolicyOverride.builder()
                .businessId(b.getId()).commitmentReference(commitmentB)
                .policyId(policyA.getId()).policyVersionId(versionA.getId()) // wrong business's policy/version
                .requestedBy(UUID.randomUUID()).reason("cross business").build();

        assertThrows(DataIntegrityViolationException.class,
                () -> policyOverrideRepository.saveAndFlush(crossBusiness),
                "fk_policy_overrides_policy must reject a mismatched (business_id, policy_id) pair");
    }

    @Test
    void duplicateActiveOverrideForTheSameTupleIsRejectedByTheUniqueIndex() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), true, true, true);
        PolicyVersion version = currentVersion(policy.getId());
        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");

        policyEngine.requestOverride(business.getId(), commitment, policy.getId(), version.getId(),
                UUID.randomUUID(), "First request.", false);

        PolicyOverride duplicate = PolicyOverride.builder()
                .businessId(business.getId()).commitmentReference(commitment)
                .policyId(policy.getId()).policyVersionId(version.getId())
                .requestedBy(UUID.randomUUID()).reason("Duplicate concurrent request.").status("PENDING").build();

        assertThrows(DataIntegrityViolationException.class,
                () -> policyOverrideRepository.saveAndFlush(duplicate),
                "uq_policy_overrides_active_tuple must reject a second PENDING/APPROVED row for the same tuple");
    }
}
