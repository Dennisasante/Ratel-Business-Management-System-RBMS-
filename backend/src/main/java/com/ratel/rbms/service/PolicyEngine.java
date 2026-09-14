package com.ratel.rbms.service;

import com.ratel.rbms.entity.Policy;
import com.ratel.rbms.entity.PolicyCommitment;
import com.ratel.rbms.entity.PolicyDisclosure;
import com.ratel.rbms.entity.PolicyOverride;
import com.ratel.rbms.entity.PolicyVersion;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.PolicyCommitmentRepository;
import com.ratel.rbms.repository.PolicyDisclosureRepository;
import com.ratel.rbms.repository.PolicyOverrideRepository;
import com.ratel.rbms.repository.PolicyRepository;
import com.ratel.rbms.repository.PolicyVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 3/4 — the deterministic Policy/Disclosure/Commitment/Override
 * engine (architecture proposal §G; Phase 4 frozen after Revision 1-4 + Final Corrections).
 * Every method takes an explicit, typed {@code businessId} and evaluates purely against the
 * schema — no prompt, no model call, no natural-language interpretation anywhere in this class.
 * What options exist and whether a substitution is valid remain Phase 2's own responsibility
 * (Offering/PackageComponent/Option/SubstitutionRule/QuantityRule) — this class never re-derives
 * or duplicates that; it only answers whether a policy must be disclosed/acknowledged (and, as
 * of Phase 4, whether the current transaction attempt is actually allowed to proceed) before a
 * commitment can be linked to a real transaction.
 *
 * <p><b>Two layers (Revision 4 §5), never conflated:</b> {@link #applicablePolicies} and
 * {@link #unsatisfiedRequiredPolicies}/{@link #canProceed} are pre-flight/UX helpers — useful for
 * showing a caller what's required, but advisory only, and not version-of-record about customer
 * identity. {@link #evaluateGate} is the one authoritative, final check: version-aware,
 * customer-identity-aware, override-aware, and the only method a transaction-creating service is
 * allowed to trust immediately before persisting a row.
 */
@Service
public class PolicyEngine {

    private final PolicyRepository policyRepository;
    private final PolicyVersionRepository policyVersionRepository;
    private final PolicyCommitmentRepository policyCommitmentRepository;
    private final PolicyDisclosureRepository policyDisclosureRepository;
    private final PolicyOverrideRepository policyOverrideRepository;

    public PolicyEngine(
            PolicyRepository policyRepository,
            PolicyVersionRepository policyVersionRepository,
            PolicyCommitmentRepository policyCommitmentRepository,
            PolicyDisclosureRepository policyDisclosureRepository,
            PolicyOverrideRepository policyOverrideRepository
    ) {
        this.policyRepository = policyRepository;
        this.policyVersionRepository = policyVersionRepository;
        this.policyCommitmentRepository = policyCommitmentRepository;
        this.policyDisclosureRepository = policyDisclosureRepository;
        this.policyOverrideRepository = policyOverrideRepository;
    }

    // ==================== Commitment: open ====================

    /** Legacy/simple form — transactionType left null, bound later on first {@link #evaluateGate} use. */
    @Transactional
    public UUID beginCommitment(UUID businessId) {
        return beginCommitment(businessId, null);
    }

    /**
     * Mints and registers a fresh commitment reference to this business, before any disclosure
     * exists for it. The engine owns minting deliberately — a caller-supplied reference would
     * reopen the exact cross-business-reuse risk the composite FK on PolicyDisclosure exists to
     * close. {@code transactionType} may be supplied now or left null and bound on first
     * {@link #evaluateGate} use — either way, once bound it cannot be rebound to a different
     * value (Revision 4 §2).
     */
    @Transactional
    public UUID beginCommitment(UUID businessId, String transactionType) {
        PolicyCommitment commitment = policyCommitmentRepository.save(
                PolicyCommitment.builder().businessId(businessId).transactionType(transactionType).build());
        return commitment.getCommitmentReference();
    }

    // ==================== Pre-flight / UX layer (advisory only) ====================

    public List<Policy> applicablePolicies(UUID businessId, String appliesToAction, UUID offeringId) {
        return policyRepository.findApplicable(businessId, appliesToAction, offeringId);
    }

    /**
     * Restaurant-AI-demo phase — the actual customer-facing text of every applicable policy's
     * CURRENT version, for a tool/UI that needs to literally show/tell a customer what a policy
     * says (not just whether one is outstanding, which {@link #unsatisfiedRequiredPolicies} and
     * {@link #canProceed} already answer). Read-only, advisory — same two-layer distinction as
     * every other pre-flight method here; never a substitute for {@link #evaluateGate}.
     */
    public List<PolicyContent> applicablePolicyContent(UUID businessId, String appliesToAction, UUID offeringId) {
        return applicablePolicies(businessId, appliesToAction, offeringId).stream()
                .map(policy -> {
                    PolicyVersion current = currentVersion(policy.getId());
                    return new PolicyContent(policy.getId(), policy.getPolicyKey(), current.getTitle(), current.getContent(),
                            current.isRequiresAcknowledgement(), current.isBlocksTransaction());
                })
                .toList();
    }

    public record PolicyContent(UUID policyId, String policyKey, String title, String content,
                                 boolean requiresAcknowledgement, boolean blocksTransaction) {
    }

    /**
     * Applicable policies whose CURRENT version requires acknowledgement and have not yet been
     * acknowledged (at that current version) for this specific commitment attempt. Advisory
     * only — see the class-level note on the two-layer distinction.
     */
    public List<Policy> unsatisfiedRequiredPolicies(UUID businessId, String appliesToAction, UUID offeringId,
                                                      UUID commitmentReference) {
        return applicablePolicies(businessId, appliesToAction, offeringId).stream()
                .filter(policy -> {
                    PolicyVersion current = currentVersion(policy.getId());
                    if (!current.isRequiresAcknowledgement()) {
                        return false;
                    }
                    return !isAcknowledgedForCommitment(businessId, policy.getId(), current.getId(), commitmentReference);
                })
                .toList();
    }

    /**
     * True unless an applicable policy's CURRENT version both requires acknowledgement AND
     * blocks the transaction, and remains unacknowledged (at that current version) for this
     * commitment. Advisory only — never customer-identity-aware, never override-aware, never
     * locks anything. See {@link #evaluateGate} for the authoritative check.
     */
    public boolean canProceed(UUID businessId, String appliesToAction, UUID offeringId, UUID commitmentReference) {
        return applicablePolicies(businessId, appliesToAction, offeringId).stream()
                .noneMatch(policy -> {
                    PolicyVersion current = currentVersion(policy.getId());
                    if (!current.isRequiresAcknowledgement() || !current.isBlocksTransaction()) {
                        return false;
                    }
                    return !isAcknowledgedForCommitment(businessId, policy.getId(), current.getId(), commitmentReference);
                });
    }

    // ==================== Disclosure / acknowledgement ====================

    /**
     * Records that a policy's CURRENT version was shown, under a specific commitment attempt.
     * The version is resolved and frozen onto this row now — a later edit to the policy can
     * never change what this disclosure recorded as having been shown.
     */
    @Transactional
    public PolicyDisclosure recordDisclosure(UUID businessId, UUID policyId, UUID commitmentReference,
                                              UUID customerId, UUID conversationId, String actor, String channel) {
        PolicyVersion current = currentVersion(policyId);
        PolicyDisclosure disclosure = PolicyDisclosure.builder()
                .businessId(businessId)
                .policyId(policyId)
                .policyVersionId(current.getId())
                .commitmentReference(commitmentReference)
                .customerId(customerId)
                .conversationId(conversationId)
                .actor(actor)
                .channel(channel)
                .disclosedAt(Instant.now())
                .build();
        return policyDisclosureRepository.save(disclosure);
    }

    /**
     * First acknowledgement wins — a second call on an already-acknowledged disclosure is a
     * no-op that preserves the original acknowledgedAt/acknowledgedByType/acknowledgedById,
     * never overwrites them. The historical fact is the first genuine acknowledgement event,
     * not the most recent call.
     */
    @Transactional
    public void recordAcknowledgement(UUID businessId, UUID disclosureId, String acknowledgedByType, UUID acknowledgedById) {
        PolicyDisclosure disclosure = policyDisclosureRepository.findByIdAndBusinessId(disclosureId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Disclosure not found."));
        if (disclosure.getAcknowledgedAt() != null) {
            return; // already acknowledged — first acknowledgement wins, this call is a no-op.
        }
        disclosure.setAcknowledgedAt(Instant.now());
        disclosure.setAcknowledgedByType(acknowledgedByType);
        disclosure.setAcknowledgedById(acknowledgedById);
        policyDisclosureRepository.save(disclosure);
    }

    // ==================== Final gate (authoritative — Revision 4 §5) ====================

    /**
     * The one authoritative ALLOW/BLOCK decision, evaluated immediately before a transaction is
     * persisted. Must be called from inside the SAME {@code @Transactional} method that will go
     * on to persist the transaction row and then call {@link #linkCommitmentToTransaction} — the
     * pessimistic lock acquired here (Revision 4 §2) is held for the rest of that transaction,
     * and override consumption performed here only becomes durable if that same transaction
     * commits (Revision 4 §10 — a commitment/override cannot be consumed for a transaction that
     * does not persist).
     *
     * <p>Customer-identity rule (Final Corrections §1): evidence whose {@code acknowledgedByType
     * = 'CUSTOMER'} only satisfies a pair when {@code customerId} is non-null AND equals that
     * evidence's {@code acknowledgedById}. A null {@code customerId} against such evidence is
     * itself a BLOCK, not a skipped check. {@code STAFF}-type evidence is unaffected by
     * {@code customerId} entirely.
     *
     * @param customerId the transaction's own customer, or null if none is known/applicable yet
     */
    @Transactional
    public GateResult evaluateGate(UUID businessId, String appliesToAction, UUID offeringId,
                                    UUID commitmentReference, String transactionType, UUID customerId) {
        List<Policy> applicable = applicablePolicies(businessId, appliesToAction, offeringId);
        if (applicable.isEmpty()) {
            // Nothing to enforce — a business with no policies configured for this action must
            // see zero behaviour change, even with no commitment opened at all.
            return GateResult.allow();
        }
        if (commitmentReference == null) {
            return GateResult.block("Policy disclosure is required before this transaction can proceed; no commitment was opened.");
        }

        PolicyCommitment commitment = policyCommitmentRepository.findByCommitmentReferenceForUpdate(commitmentReference, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Commitment not found for this business."));

        if (!"ACTIVE".equals(commitment.getStatus())) {
            return GateResult.block("Commitment is no longer active (status=" + commitment.getStatus() + "); open a new commitment.");
        }

        if (commitment.getTransactionType() == null) {
            commitment.setTransactionType(transactionType);
            policyCommitmentRepository.save(commitment);
        } else if (!commitment.getTransactionType().equals(transactionType)) {
            return GateResult.block("Commitment was opened for a different transaction type (" + commitment.getTransactionType() + ").");
        }

        List<String> reasons = new ArrayList<>();
        List<UUID> overridesToConsume = new ArrayList<>();

        for (Policy policy : applicable) {
            PolicyVersion version = currentVersion(policy.getId());
            if (!version.isRequiresAcknowledgement()) {
                continue;
            }
            if (findSatisfyingDisclosure(businessId, policy.getId(), version.getId(), commitmentReference, customerId).isPresent()) {
                continue; // satisfied directly
            }
            if (!version.isBlocksTransaction()) {
                continue; // required but not blocking — reportable elsewhere, never a gate reason
            }
            if (version.isStaffOverridable()) {
                Optional<PolicyOverride> override = policyOverrideRepository
                        .findByBusinessIdAndCommitmentReferenceAndPolicyIdAndPolicyVersionIdAndStatus(
                                businessId, commitmentReference, policy.getId(), version.getId(), "APPROVED");
                if (override.isPresent()) {
                    overridesToConsume.add(override.get().getId());
                    continue;
                }
            }
            reasons.add("Policy \"" + policy.getPolicyKey() + "\" (v" + version.getVersionNumber()
                    + ") requires disclosure and acknowledgement.");
        }

        if (!reasons.isEmpty()) {
            return GateResult.block(reasons);
        }

        for (UUID overrideId : overridesToConsume) {
            int updated = policyOverrideRepository.consume(overrideId, businessId, Instant.now());
            if (updated == 0) {
                // Lost a race against another consumer of the same override (or it was
                // cancelled/expired concurrently) — fail closed, never fail open.
                return GateResult.block("A required override was consumed or withdrawn concurrently; please retry.");
            }
        }

        return GateResult.allow();
    }

    /**
     * Step 8 of the frozen gate sequence: called by the transaction service AFTER it has
     * successfully persisted the real transaction row (and, ordinarily, after a prior
     * {@link #evaluateGate} call returned {@code allowed() == true} in this same
     * {@code @Transactional} method). Backfills every disclosure written under this commitment
     * attempt with the real transaction identity, then completes the commitment. Rejects an
     * attempt to complete a commitment that is not currently ACTIVE — a terminal commitment can
     * never be reused (Revision 4 §12, invariant 1).
     */
    @Transactional
    public void linkCommitmentToTransaction(UUID businessId, UUID commitmentReference,
                                             String transactionType, UUID transactionId) {
        // Locked, not a plain read: without this, two concurrent completions of the same
        // commitment (e.g. a duplicate/retried request) could each read ACTIVE before either
        // writes, and both proceed to "complete" it — the second silently overwriting the
        // first's transactionId rather than being rejected. The lock makes the two genuinely
        // serialize: the second sees the first's committed COMPLETED status and is rejected.
        PolicyCommitment commitment = policyCommitmentRepository.findByCommitmentReferenceForUpdate(commitmentReference, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Commitment not found for this business."));
        if (!"ACTIVE".equals(commitment.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Commitment is not active (status=" + commitment.getStatus() + ").");
        }
        policyDisclosureRepository.linkToTransaction(businessId, commitmentReference, transactionType, transactionId);
        commitment.setStatus("COMPLETED");
        commitment.setCompletedAt(Instant.now());
        policyCommitmentRepository.save(commitment);
    }

    // ==================== Commitment lifecycle (abandon / invalidate) ====================

    /** Explicit "the customer/staff walked away from this attempt" transition — always legal from ACTIVE, a no-op otherwise. */
    @Transactional
    public void abandonCommitment(UUID businessId, UUID commitmentReference) {
        transitionCommitment(businessId, commitmentReference, "ABANDONED");
    }

    /**
     * The transaction state changed in a way that invalidates this commitment's evidence
     * (Revision 4 §9's re-evaluation model — e.g. a policy version changed, or the applicable
     * set lost a member that was already satisfied). Also bulk-expires any still-outstanding
     * (PENDING/APPROVED) override tied to this commitment — an override for a stale requirement
     * must not remain claimable later.
     */
    @Transactional
    public void invalidateCommitment(UUID businessId, UUID commitmentReference) {
        if (transitionCommitment(businessId, commitmentReference, "INVALIDATED")) {
            policyOverrideRepository.expireOutstanding(businessId, commitmentReference);
        }
    }

    private boolean transitionCommitment(UUID businessId, UUID commitmentReference, String newStatus) {
        PolicyCommitment commitment = policyCommitmentRepository.findByCommitmentReferenceForUpdate(commitmentReference, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Commitment not found for this business."));
        if (!"ACTIVE".equals(commitment.getStatus())) {
            return false; // already terminal — a terminal commitment can never be re-transitioned
        }
        commitment.setStatus(newStatus);
        policyCommitmentRepository.save(commitment);
        return true;
    }

    // ==================== Staff override (Revision 4 §9 / Final Corrections §2) ====================

    /**
     * Requests permission to proceed past a specific, currently-blocking policy version for one
     * specific commitment. {@code requesterIsOwner} deliberately deviates from
     * {@code SaleService.refund()}'s own "Owner self-authorizes, no record created" convention:
     * here a real, evidenced {@link PolicyOverride} row is always created — self-decided when
     * the requester is already the Owner, never silently skipped — because policy-compliance
     * evidence must never have a gap (Final Corrections §2).
     */
    @Transactional
    public UUID requestOverride(UUID businessId, UUID commitmentReference, UUID policyId, UUID policyVersionId,
                                 UUID requestedBy, String reason, boolean requesterIsOwner) {
        policyCommitmentRepository.findByCommitmentReferenceAndBusinessId(commitmentReference, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Commitment not found for this business."));
        Instant now = Instant.now();
        PolicyOverride override = PolicyOverride.builder()
                .businessId(businessId)
                .commitmentReference(commitmentReference)
                .policyId(policyId)
                .policyVersionId(policyVersionId)
                .requestedBy(requestedBy)
                .reason(reason)
                .requestedAt(now)
                .status(requesterIsOwner ? "APPROVED" : "PENDING")
                .decidedBy(requesterIsOwner ? requestedBy : null)
                .decidedAt(requesterIsOwner ? now : null)
                .build();
        return policyOverrideRepository.save(override).getId();
    }

    /** OWNER-only at the controller layer (see PolicyController) — this method itself performs no role check. */
    @Transactional
    public void approveOverride(UUID businessId, UUID overrideId, UUID decidedBy, String note) {
        int updated = policyOverrideRepository.decide(overrideId, businessId, "APPROVED", decidedBy, Instant.now(), note);
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Override request was already decided (or does not exist for this business).");
        }
    }

    @Transactional
    public void rejectOverride(UUID businessId, UUID overrideId, UUID decidedBy, String note) {
        int updated = policyOverrideRepository.decide(overrideId, businessId, "REJECTED", decidedBy, Instant.now(), note);
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Override request was already decided (or does not exist for this business).");
        }
    }

    @Transactional
    public void cancelOverride(UUID businessId, UUID overrideId) {
        int updated = policyOverrideRepository.cancel(overrideId, businessId);
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Override request can no longer be cancelled (already decided/consumed/terminal).");
        }
    }

    // ==================== Internal helpers ====================

    private PolicyVersion currentVersion(UUID policyId) {
        return policyVersionRepository.findTopByPolicyIdOrderByVersionNumberDesc(policyId)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Policy has no version — this should never happen; a Policy and its first "
                                + "PolicyVersion must always be created together."));
    }

    private boolean isAcknowledgedForCommitment(UUID businessId, UUID policyId, UUID policyVersionId, UUID commitmentReference) {
        return policyDisclosureRepository
                .findAllByBusinessIdAndPolicyIdAndPolicyVersionIdAndCommitmentReference(businessId, policyId, policyVersionId, commitmentReference)
                .stream()
                .anyMatch(d -> d.getAcknowledgedAt() != null);
    }

    /** Version-aware AND customer-identity-aware — used only by {@link #evaluateGate}, the authoritative layer. */
    private Optional<PolicyDisclosure> findSatisfyingDisclosure(UUID businessId, UUID policyId, UUID policyVersionId,
                                                                  UUID commitmentReference, UUID customerId) {
        return policyDisclosureRepository
                .findAllByBusinessIdAndPolicyIdAndPolicyVersionIdAndCommitmentReference(businessId, policyId, policyVersionId, commitmentReference)
                .stream()
                .filter(d -> d.getAcknowledgedAt() != null)
                .filter(d -> satisfiesCustomerIdentity(d, customerId))
                .findFirst();
    }

    /** Final Corrections §1's truth table, as code. */
    private boolean satisfiesCustomerIdentity(PolicyDisclosure disclosure, UUID customerId) {
        if (!"CUSTOMER".equals(disclosure.getAcknowledgedByType())) {
            return true; // STAFF-type evidence is never customer-scoped — unaffected by customerId
        }
        return customerId != null && customerId.equals(disclosure.getAcknowledgedById());
    }
}
