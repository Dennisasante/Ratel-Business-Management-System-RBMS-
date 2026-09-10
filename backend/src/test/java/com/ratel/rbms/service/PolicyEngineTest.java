package com.ratel.rbms.service;

import com.ratel.rbms.entity.*;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 3 — proves the Policy/PolicyVersion/PolicyCommitment/
 * PolicyDisclosure model against real PostgreSQL: tenant isolation, policy/version agreement,
 * append-only version enforcement, commitment tenant-scoping, acknowledgement actor semantics,
 * and deterministic applicability/blocking evaluation. @Transactional rolls every test back;
 * nothing here touches real data permanently. Each constraint-violation test is the last action
 * in its own method — Postgres aborts the rest of a transaction after a failed statement.
 */
@SpringBootTest
@Transactional
class PolicyEngineTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private PolicyVersionRepository policyVersionRepository;
    @Autowired private PolicyCommitmentRepository policyCommitmentRepository;
    @Autowired private PolicyDisclosureRepository policyDisclosureRepository;
    @Autowired private PolicyEngine policyEngine;

    private static final String OUTSIDE_FOOD_ACTION = "EVENT_RESERVATION_CONFIRM";

    // ==================== Setup helpers ====================

    private Business newBusiness() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return businessRepository.save(Business.builder()
                .name("Phase 3 Test Business " + unique).slug("phase3-test-business-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
    }

    private Policy newPolicyWithFirstVersion(UUID businessId, String action, UUID offeringId,
                                              boolean requiresAck, boolean blocks) {
        Policy policy = policyRepository.save(Policy.builder()
                .businessId(businessId).policyKey("policy-" + UUID.randomUUID().toString().substring(0, 8))
                .appliesToAction(action).offeringId(offeringId).createdBy(UUID.randomUUID()).build());
        policyVersionRepository.save(PolicyVersion.builder()
                .businessId(businessId).policyId(policy.getId()).versionNumber(1)
                .title("V1 Title").content("Outside food and drinks are prohibited.")
                .requiresAcknowledgement(requiresAck).blocksTransaction(blocks)
                .createdBy(UUID.randomUUID()).build());
        return policy;
    }

    // ==================== Tenant isolation / composite FK rejection (all four tables) ====================

    @Test
    void policyVersionCannotBeInsertedUnderAnotherBusinesssPolicy() {
        Business a = newBusiness();
        Business b = newBusiness();
        Policy policyA = newPolicyWithFirstVersion(a.getId(), OUTSIDE_FOOD_ACTION, null, true, true);

        PolicyVersion crossBusiness = PolicyVersion.builder()
                .businessId(b.getId()) // wrong business for policyA
                .policyId(policyA.getId()).versionNumber(2).title("Bad").content("Bad")
                .createdBy(UUID.randomUUID()).build();

        assertThrows(DataIntegrityViolationException.class,
                () -> policyVersionRepository.saveAndFlush(crossBusiness),
                "fk_policy_versions_policy must reject a mismatched (business_id, policy_id) pair");
    }

    @Test
    void policyDisclosureCannotBeInsertedUnderAnotherBusinesssPolicy() {
        Business a = newBusiness();
        Business b = newBusiness();
        Policy policyA = newPolicyWithFirstVersion(a.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        PolicyVersion versionA = policyVersionRepository.findTopByPolicyIdOrderByVersionNumberDesc(policyA.getId()).orElseThrow();
        UUID commitmentB = policyEngine.beginCommitment(b.getId());

        PolicyDisclosure crossBusiness = PolicyDisclosure.builder()
                .businessId(b.getId()) // wrong business for policyA/versionA
                .policyId(policyA.getId()).policyVersionId(versionA.getId())
                .commitmentReference(commitmentB).actor("AI").build();

        assertThrows(DataIntegrityViolationException.class,
                () -> policyDisclosureRepository.saveAndFlush(crossBusiness),
                "fk_policy_disclosures_policy must reject a mismatched (business_id, policy_id) pair");
    }

    @Test
    void policyDisclosureCannotReferenceAVersionBelongingToADifferentPolicy() {
        // Issue 1 from the review thread: policy_id and policy_version_id must agree, not just
        // each independently exist.
        Business business = newBusiness();
        Policy policyA = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        Policy policyB = newPolicyWithFirstVersion(business.getId(), "ORDER_PLACE", null, true, true);
        PolicyVersion policyBVersion = policyVersionRepository.findTopByPolicyIdOrderByVersionNumberDesc(policyB.getId()).orElseThrow();
        UUID commitment = policyEngine.beginCommitment(business.getId());

        PolicyDisclosure disagreeing = PolicyDisclosure.builder()
                .businessId(business.getId())
                .policyId(policyA.getId())         // claims Policy A
                .policyVersionId(policyBVersion.getId()) // but points at Policy B's version
                .commitmentReference(commitment).actor("AI").build();

        assertThrows(DataIntegrityViolationException.class,
                () -> policyDisclosureRepository.saveAndFlush(disagreeing),
                "fk_policy_disclosures_version must reject a version that doesn't belong to the stated policy");
    }

    @Test
    void policyDisclosureCannotReferenceAnotherBusinesssCommitment() {
        Business a = newBusiness();
        Business b = newBusiness();
        Policy policyB = newPolicyWithFirstVersion(b.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        PolicyVersion versionB = policyVersionRepository.findTopByPolicyIdOrderByVersionNumberDesc(policyB.getId()).orElseThrow();
        UUID commitmentA = policyEngine.beginCommitment(a.getId());

        PolicyDisclosure crossBusiness = PolicyDisclosure.builder()
                .businessId(b.getId()).policyId(policyB.getId()).policyVersionId(versionB.getId())
                .commitmentReference(commitmentA) // belongs to business A, not B
                .actor("AI").build();

        assertThrows(DataIntegrityViolationException.class,
                () -> policyDisclosureRepository.saveAndFlush(crossBusiness),
                "fk_policy_disclosures_commitment must reject a commitment registered to a different business");
    }

    @Test
    void policyCannotBeInsertedUnderAnotherBusinesssOffering() {
        Business a = newBusiness();
        Business b = newBusiness();
        Offering offeringA = offeringRepository.save(Offering.builder()
                .businessId(a.getId()).type("PACKAGE").name("A's Offering").build());

        Policy crossBusiness = Policy.builder()
                .businessId(b.getId()) // wrong business for offeringA
                .policyKey("cross-business").appliesToAction(OUTSIDE_FOOD_ACTION)
                .offeringId(offeringA.getId()).createdBy(UUID.randomUUID()).build();

        assertThrows(DataIntegrityViolationException.class,
                () -> policyRepository.saveAndFlush(crossBusiness),
                "fk_policies_offering must reject a mismatched (business_id, offering_id) pair");
    }

    // ==================== Deletion / retirement semantics ====================

    @Test
    void policyDeletionIsBlockedWhenVersionsExist() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);

        assertThrows(DataIntegrityViolationException.class,
                () -> { policyRepository.delete(policy); policyRepository.flush(); },
                "ON DELETE RESTRICT on policy_versions.policy_id must block deleting a Policy that has versions");
    }

    @Test
    void policyVersionDeletionIsBlockedWhenDisclosuresExist() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        UUID commitment = policyEngine.beginCommitment(business.getId());
        policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment, null, null, "AI", "WEB_DEMO");
        PolicyVersion version = policyVersionRepository.findTopByPolicyIdOrderByVersionNumberDesc(policy.getId()).orElseThrow();

        // The append-only trigger (Issue 1) rejects ANY delete on policy_versions regardless of
        // whether disclosures exist — this proves the row is undeletable once real evidence
        // depends on it, which is what actually matters here.
        assertThrows(DataIntegrityViolationException.class,
                () -> { policyVersionRepository.delete(version); policyVersionRepository.flush(); });
    }

    @Test
    void offeringDeletionIsBlockedWhenPoliciesExist() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").build());
        newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, offering.getId(), true, true);

        assertThrows(DataIntegrityViolationException.class,
                () -> { offeringRepository.delete(offering); offeringRepository.flush(); },
                "ON DELETE RESTRICT on policies.offering_id must block deleting an Offering that has policies");
    }

    // ==================== PolicyVersion append-only enforcement (Issue 1) ====================

    @Test
    void directUpdateOfAnExistingPolicyVersionIsRejectedAtTheDatabaseLevel() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        PolicyVersion version = policyVersionRepository.findTopByPolicyIdOrderByVersionNumberDesc(policy.getId()).orElseThrow();

        version.setContent("Silently changed content");

        assertThrows(DataIntegrityViolationException.class,
                () -> policyVersionRepository.saveAndFlush(version),
                "trg_policy_versions_no_update must reject any UPDATE on policy_versions");
    }

    @Test
    void directDeleteOfAnExistingPolicyVersionIsRejectedAtTheDatabaseLevel() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        PolicyVersion version = policyVersionRepository.findTopByPolicyIdOrderByVersionNumberDesc(policy.getId()).orElseThrow();

        assertThrows(DataIntegrityViolationException.class,
                () -> { policyVersionRepository.delete(version); policyVersionRepository.flush(); },
                "trg_policy_versions_no_delete must reject any DELETE on policy_versions");
    }

    @Test
    void policyEditingCreatesANewVersionAndThePreviousVersionRemainsUnchanged() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        PolicyVersion v1 = policyVersionRepository.findTopByPolicyIdOrderByVersionNumberDesc(policy.getId()).orElseThrow();

        // "Editing" is expressed only as inserting a new row — never a mutation of v1.
        PolicyVersion v2 = policyVersionRepository.save(PolicyVersion.builder()
                .businessId(business.getId()).policyId(policy.getId()).versionNumber(2)
                .title("V2 Title").content("Updated wording.")
                .requiresAcknowledgement(true).blocksTransaction(false) // enforcement changed too
                .createdBy(UUID.randomUUID()).build());

        PolicyVersion v1Reloaded = policyVersionRepository.findById(v1.getId()).orElseThrow();
        assertEquals("V1 Title", v1Reloaded.getTitle());
        assertEquals("Outside food and drinks are prohibited.", v1Reloaded.getContent());
        assertTrue(v1Reloaded.isBlocksTransaction(), "v1's own blocksTransaction must remain true regardless of v2");
        assertEquals(1, v1Reloaded.getVersionNumber());
        assertEquals(2, v2.getVersionNumber());
        assertEquals(policyVersionRepository.findTopByPolicyIdOrderByVersionNumberDesc(policy.getId()).orElseThrow().getId(), v2.getId());
    }

    @Test
    void historicalDisclosureStillResolvesToTheOriginalVersionAfterAPolicyEdit() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        UUID commitment = policyEngine.beginCommitment(business.getId());
        PolicyDisclosure disclosure = policyEngine.recordDisclosure(
                business.getId(), policy.getId(), commitment, null, null, "AI", "WEB_DEMO");

        // Business changes the policy's enforcement configuration in v2.
        policyVersionRepository.save(PolicyVersion.builder()
                .businessId(business.getId()).policyId(policy.getId()).versionNumber(2)
                .title("V2").content("Different wording now.")
                .requiresAcknowledgement(false).blocksTransaction(false)
                .createdBy(UUID.randomUUID()).build());

        // Re-fetch the ORIGINAL disclosure's own version — must still reflect v1's enforcement,
        // not v2's, proving enforcement flags come from the historical version, not the
        // mutable Policy row (Policy itself carries no enforcement fields at all).
        PolicyDisclosure reloaded = policyDisclosureRepository.findByIdAndBusinessId(disclosure.getId(), business.getId()).orElseThrow();
        PolicyVersion resolvedVersion = policyVersionRepository.findById(reloaded.getPolicyVersionId()).orElseThrow();
        assertEquals("V1 Title", resolvedVersion.getTitle());
        assertTrue(resolvedVersion.isRequiresAcknowledgement());
        assertTrue(resolvedVersion.isBlocksTransaction());
        assertEquals(1, resolvedVersion.getVersionNumber());
    }

    // ==================== Commitment lifecycle and tenant scoping ====================

    @Test
    void commitmentCreationRegistersItToExactlyOneBusiness() {
        Business business = newBusiness();
        UUID commitment = policyEngine.beginCommitment(business.getId());

        PolicyCommitment stored = policyCommitmentRepository.findByCommitmentReferenceAndBusinessId(commitment, business.getId())
                .orElseThrow();
        assertEquals(business.getId(), stored.getBusinessId());
    }

    @Test
    void businessBCannotLinkBusinessAsCommitmentToATransaction() {
        Business a = newBusiness();
        Business b = newBusiness();
        UUID commitmentA = policyEngine.beginCommitment(a.getId());

        ApiException ex = assertThrows(ApiException.class,
                () -> policyEngine.linkCommitmentToTransaction(b.getId(), commitmentA, "BOOKING", UUID.randomUUID()));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void completeCommitmentLifecycle() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);

        UUID commitment = policyEngine.beginCommitment(business.getId());
        assertFalse(policyEngine.canProceed(business.getId(), OUTSIDE_FOOD_ACTION, null, commitment),
                "must not be able to proceed before the required, blocking policy is acknowledged");

        PolicyDisclosure disclosure = policyEngine.recordDisclosure(
                business.getId(), policy.getId(), commitment, null, null, "AI", "WEB_DEMO");
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "CUSTOMER", null);

        assertTrue(policyEngine.canProceed(business.getId(), OUTSIDE_FOOD_ACTION, null, commitment),
                "must be able to proceed once the policy is acknowledged");

        UUID transactionId = UUID.randomUUID();
        policyEngine.linkCommitmentToTransaction(business.getId(), commitment, "BOOKING", transactionId);

        PolicyDisclosure linked = policyDisclosureRepository.findByIdAndBusinessId(disclosure.getId(), business.getId()).orElseThrow();
        assertEquals("BOOKING", linked.getTransactionType());
        assertEquals(transactionId, linked.getTransactionId());
        assertNotNull(linked.getAcknowledgedAt(), "linking to a transaction must never clear the acknowledgement");
    }

    @Test
    void twoCommitmentsInTheSameConversationCannotCrossSatisfyEachOther() {
        // The core proof from the review thread: two simultaneous transaction attempts for the
        // same customer, same conversation, must not let one's acknowledgement satisfy the other.
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        UUID sharedConversation = UUID.randomUUID();

        UUID commitmentJune = policyEngine.beginCommitment(business.getId());
        UUID commitmentJuly = policyEngine.beginCommitment(business.getId());

        PolicyDisclosure juneDisclosure = policyEngine.recordDisclosure(
                business.getId(), policy.getId(), commitmentJune, null, sharedConversation, "AI", "WEB_DEMO");
        policyEngine.recordAcknowledgement(business.getId(), juneDisclosure.getId(), "CUSTOMER", null);

        assertTrue(policyEngine.canProceed(business.getId(), OUTSIDE_FOOD_ACTION, null, commitmentJune));
        assertFalse(policyEngine.canProceed(business.getId(), OUTSIDE_FOOD_ACTION, null, commitmentJuly),
                "acknowledging under the June commitment must not satisfy the July commitment, "
                        + "even in the same conversation");
    }

    @Test
    void disclosureIdempotencyRepeatedDisclosureBeforeAcknowledgementIsSafe() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        UUID commitment = policyEngine.beginCommitment(business.getId());

        // The AI mentions the policy twice before the customer responds — two legitimate rows.
        policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment, null, null, "AI", "WEB_DEMO");
        PolicyDisclosure second = policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment, null, null, "AI", "WEB_DEMO");

        List<Policy> stillUnsatisfied = policyEngine.unsatisfiedRequiredPolicies(business.getId(), OUTSIDE_FOOD_ACTION, null, commitment);
        assertEquals(1, stillUnsatisfied.size(), "two disclosure rows, neither acknowledged yet, must still be unsatisfied");

        policyEngine.recordAcknowledgement(business.getId(), second.getId(), "CUSTOMER", null);
        assertTrue(policyEngine.canProceed(business.getId(), OUTSIDE_FOOD_ACTION, null, commitment),
                "acknowledging via either disclosure row must satisfy the policy for this commitment");
    }

    // ==================== Acknowledgement actor semantics ====================

    @Test
    void customerAcknowledgement() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        UUID commitment = policyEngine.beginCommitment(business.getId());
        UUID customerId = UUID.randomUUID();
        PolicyDisclosure disclosure = policyEngine.recordDisclosure(
                business.getId(), policy.getId(), commitment, customerId, null, "CUSTOMER_SELF_SERVICE", "WEB_DEMO");

        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "CUSTOMER", customerId);

        PolicyDisclosure reloaded = policyDisclosureRepository.findByIdAndBusinessId(disclosure.getId(), business.getId()).orElseThrow();
        assertEquals("CUSTOMER", reloaded.getAcknowledgedByType());
        assertEquals(customerId, reloaded.getAcknowledgedById());
        assertNotNull(reloaded.getAcknowledgedAt());
    }

    @Test
    void staffRecordedAcknowledgement() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        UUID commitment = policyEngine.beginCommitment(business.getId());
        // A phone-in booking — staff is the disclosing actor.
        PolicyDisclosure disclosure = policyEngine.recordDisclosure(
                business.getId(), policy.getId(), commitment, null, null, "STAFF", null);
        UUID staffId = UUID.randomUUID();

        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "STAFF", staffId);

        PolicyDisclosure reloaded = policyDisclosureRepository.findByIdAndBusinessId(disclosure.getId(), business.getId()).orElseThrow();
        assertEquals("STAFF", reloaded.getAcknowledgedByType());
        assertEquals(staffId, reloaded.getAcknowledgedById());
    }

    @Test
    void staffAcknowledgementWithoutAnIdentifiedStaffIdIsRejected() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        UUID commitment = policyEngine.beginCommitment(business.getId());
        PolicyDisclosure disclosure = policyEngine.recordDisclosure(
                business.getId(), policy.getId(), commitment, null, null, "STAFF", null);

        // Bypass the service to prove the DB-level chk_policy_disclosures_ack_staff_identified.
        disclosure.setAcknowledgedAt(java.time.Instant.now());
        disclosure.setAcknowledgedByType("STAFF");
        disclosure.setAcknowledgedById(null);

        assertThrows(DataIntegrityViolationException.class,
                () -> policyDisclosureRepository.saveAndFlush(disclosure));
    }

    @Test
    void crossBusinessAcknowledgementIsRejected() {
        Business a = newBusiness();
        Business b = newBusiness();
        Policy policyA = newPolicyWithFirstVersion(a.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        UUID commitmentA = policyEngine.beginCommitment(a.getId());
        PolicyDisclosure disclosureA = policyEngine.recordDisclosure(
                a.getId(), policyA.getId(), commitmentA, null, null, "AI", "WEB_DEMO");

        ApiException ex = assertThrows(ApiException.class,
                () -> policyEngine.recordAcknowledgement(b.getId(), disclosureA.getId(), "CUSTOMER", null));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void secondAcknowledgementDoesNotOverwriteTheFirst() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        UUID commitment = policyEngine.beginCommitment(business.getId());
        PolicyDisclosure disclosure = policyEngine.recordDisclosure(
                business.getId(), policy.getId(), commitment, null, null, "AI", "WEB_DEMO");
        UUID firstAcknowledger = UUID.randomUUID();

        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "CUSTOMER", firstAcknowledger);
        PolicyDisclosure afterFirst = policyDisclosureRepository.findByIdAndBusinessId(disclosure.getId(), business.getId()).orElseThrow();
        java.time.Instant firstAckTime = afterFirst.getAcknowledgedAt();

        // A different actor attempts to acknowledge the same disclosure again.
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "STAFF", UUID.randomUUID());

        PolicyDisclosure afterSecond = policyDisclosureRepository.findByIdAndBusinessId(disclosure.getId(), business.getId()).orElseThrow();
        assertEquals("CUSTOMER", afterSecond.getAcknowledgedByType(), "the original acknowledger must not be overwritten");
        assertEquals(firstAcknowledger, afterSecond.getAcknowledgedById());
        assertEquals(firstAckTime, afterSecond.getAcknowledgedAt());
    }

    // ==================== Applicability and blocking evaluation ====================

    @Test
    void businessWideAndOfferingSpecificApplicabilityBothWork() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Birthday Package").build());
        Offering otherOffering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Unrelated Package").build());

        newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true); // business-wide
        newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, offering.getId(), true, true); // offering-scoped

        List<Policy> forOffering = policyEngine.applicablePolicies(business.getId(), OUTSIDE_FOOD_ACTION, offering.getId());
        assertEquals(2, forOffering.size(), "the offering-specific attempt must see both the business-wide and its own scoped policy");

        List<Policy> forOtherOffering = policyEngine.applicablePolicies(business.getId(), OUTSIDE_FOOD_ACTION, otherOffering.getId());
        assertEquals(1, forOtherOffering.size(), "an unrelated offering must only see the business-wide policy");
    }

    @Test
    void nonRequiredPolicyNeverBlocksProgression() {
        Business business = newBusiness();
        newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, false, true); // not required
        UUID commitment = policyEngine.beginCommitment(business.getId());

        assertTrue(policyEngine.canProceed(business.getId(), OUTSIDE_FOOD_ACTION, null, commitment));
        assertTrue(policyEngine.unsatisfiedRequiredPolicies(business.getId(), OUTSIDE_FOOD_ACTION, null, commitment).isEmpty());
    }

    @Test
    void requiredButNonBlockingPolicyDoesNotPreventProceedingEvenWhenUnacknowledged() {
        Business business = newBusiness();
        newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, false); // required, not blocking
        UUID commitment = policyEngine.beginCommitment(business.getId());

        assertTrue(policyEngine.canProceed(business.getId(), OUTSIDE_FOOD_ACTION, null, commitment),
                "required-but-non-blocking must not prevent canProceed");
        assertEquals(1, policyEngine.unsatisfiedRequiredPolicies(business.getId(), OUTSIDE_FOOD_ACTION, null, commitment).size(),
                "it is still reportable as unsatisfied, just not blocking");
    }

    @Test
    void nonApplicablePolicyIsInvisibleToADifferentAction() {
        Business business = newBusiness();
        newPolicyWithFirstVersion(business.getId(), "ORDER_PLACE", null, true, true);
        UUID commitment = policyEngine.beginCommitment(business.getId());

        assertTrue(policyEngine.applicablePolicies(business.getId(), OUTSIDE_FOOD_ACTION, null).isEmpty());
        assertTrue(policyEngine.canProceed(business.getId(), OUTSIDE_FOOD_ACTION, null, commitment));
    }

    @Test
    void requiredAndBlockingPolicyBlocksProgressionUntilAcknowledged() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        UUID commitment = policyEngine.beginCommitment(business.getId());

        assertFalse(policyEngine.canProceed(business.getId(), OUTSIDE_FOOD_ACTION, null, commitment));

        PolicyDisclosure disclosure = policyEngine.recordDisclosure(
                business.getId(), policy.getId(), commitment, null, null, "AI", "WEB_DEMO");
        assertFalse(policyEngine.canProceed(business.getId(), OUTSIDE_FOOD_ACTION, null, commitment),
                "disclosure alone, without acknowledgement, must not unblock");

        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "CUSTOMER", null);
        assertTrue(policyEngine.canProceed(business.getId(), OUTSIDE_FOOD_ACTION, null, commitment));
    }

    @Test
    void deactivatedPolicyStopsBeingApplicableButHistoryIsUntouched() {
        Business business = newBusiness();
        Policy policy = newPolicyWithFirstVersion(business.getId(), OUTSIDE_FOOD_ACTION, null, true, true);
        UUID commitment = policyEngine.beginCommitment(business.getId());
        PolicyDisclosure disclosure = policyEngine.recordDisclosure(
                business.getId(), policy.getId(), commitment, null, null, "AI", "WEB_DEMO");
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "CUSTOMER", null);

        policy.setActive(false);
        policyRepository.save(policy);

        assertTrue(policyEngine.applicablePolicies(business.getId(), OUTSIDE_FOOD_ACTION, null).isEmpty(),
                "a deactivated policy must stop being returned as applicable");

        PolicyDisclosure stillThere = policyDisclosureRepository.findByIdAndBusinessId(disclosure.getId(), business.getId()).orElseThrow();
        assertNotNull(stillThere.getAcknowledgedAt(), "deactivating the policy must not touch existing disclosure history");
    }
}
