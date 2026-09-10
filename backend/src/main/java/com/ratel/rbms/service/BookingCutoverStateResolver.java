package com.ratel.rbms.service;

import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.enums.BookingCutoverState;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5C — the SOLE runtime interpreter and writer of
 * {@code businesses.booking_cutover_state} (frozen design, Revision 4 §3/§4). No other class may
 * call {@link Business#setBookingCutoverState}.
 *
 * <p>Every {@code BookingService} method that must branch on cutover state consults
 * {@link #useCanonical(UUID)} (or {@link #resolve(UUID)}) exactly once, at the top of the method,
 * and branches its entire behaviour from that single result — never re-derives eligibility from
 * raw verification data. AI inherits this automatically because {@code AiToolService} delegates
 * to the same {@code BookingService} methods (Revision 4 §6, re-confirmed by fresh source read
 * during Phase 5C implementation).
 *
 * <p>Read path ({@link #resolve}/{@link #useCanonical}) is deliberately unlocked — called on
 * every booking-adjacent request, it must never contend with or block on a concurrent state
 * transition. Every WRITE (transition) method instead locks the whole {@code Business} row via
 * the existing {@code BusinessRepository.findByIdForUpdate} (this codebase's own established
 * precedent, e.g. {@code BillingService.verifyPayment}), so two concurrent transition attempts on
 * the SAME business genuinely serialize — the second re-reads the first's committed state and its
 * own precondition check then correctly succeeds or fails against that up-to-date value
 * (Revision 4 §29 — "first valid transition wins; invalid transition fails safely").
 *
 * <p>Allowed transitions (frozen, Revision 4 §4):
 * <pre>
 * NOT_READY               --[clean verification]--&gt;           VERIFIED
 * VERIFIED                --[operator]--&gt;                     CANONICAL_ENABLED
 * CANONICAL_ENABLED        --[operator]--&gt;                     VERIFIED
 * VERIFIED                --[verification finds a mismatch]--&gt; NOT_READY   (not modeled as a
 *                                                                            distinct method — see
 *                                                                            markInvalid's own note)
 * (VERIFIED-or-later)     --[live defect detected]--&gt;          CANONICAL_DATA_INVALID
 * CANONICAL_DATA_INVALID  --[clean full verification]--&gt;       VERIFIED
 * CANONICAL_DATA_INVALID  --NEVER directly--&gt;                  CANONICAL_ENABLED
 * </pre>
 * LEGACY_RETIREMENT_READY/LEGACY_RETIRED are never transitioned into by this class in Phase 5C
 * (Revision 4 §6) — {@link #useCanonical} treats them as canonical purely for forward
 * compatibility, should a future phase activate them.
 */
@Service
public class BookingCutoverStateResolver {

    private final BusinessRepository businessRepository;
    private final ActivityLogService activityLogService;

    public BookingCutoverStateResolver(BusinessRepository businessRepository, ActivityLogService activityLogService) {
        this.businessRepository = businessRepository;
        this.activityLogService = activityLogService;
    }

    /** Unlocked read — safe to call on every request. */
    public BookingCutoverState resolve(UUID businessId) {
        return businessRepository.findBookingCutoverStateById(businessId).orElse(BookingCutoverState.NOT_READY);
    }

    /**
     * True only for CANONICAL_ENABLED (and, for forward-compatibility only, the two dormant
     * later-lifecycle states) — every other state (including CANONICAL_DATA_INVALID) means
     * "use legacy" (Revision 4 §4/§6).
     */
    public boolean useCanonical(UUID businessId) {
        BookingCutoverState state = resolve(businessId);
        return state == BookingCutoverState.CANONICAL_ENABLED
                || state == BookingCutoverState.LEGACY_RETIREMENT_READY
                || state == BookingCutoverState.LEGACY_RETIRED;
    }

    /**
     * Records a clean Stage 1 verification result. From NOT_READY, promotes to VERIFIED. Called
     * again later while already VERIFIED-or-later with a clean result is a no-op (re-verification
     * simply reconfirms cleanliness — nothing to transition). Refuses to run against
     * CANONICAL_DATA_INVALID — that state can only be exited via {@link #recoverFromInvalid},
     * a deliberately separate, explicit call (Revision 4 §1/§4 — "never a one-step recovery").
     */
    @Transactional
    public void markVerified(UUID businessId, VerificationResult result) {
        if (!result.clean()) {
            throw new IllegalArgumentException("markVerified requires a clean VerificationResult; use markInvalid/leave NOT_READY instead.");
        }
        Business business = lockBusiness(businessId);
        BookingCutoverState current = business.getBookingCutoverState();
        if (current == BookingCutoverState.CANONICAL_DATA_INVALID) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Business is CANONICAL_DATA_INVALID; use recoverFromInvalid, not markVerified.");
        }
        if (current == BookingCutoverState.NOT_READY) {
            writeState(business, BookingCutoverState.VERIFIED);
        }
        // else: already VERIFIED or later — clean re-verification, no transition needed.
    }

    /**
     * Records a NOT-clean Stage 1/live verification result. From NOT_READY, this is a no-op (it
     * was never verified — nothing to invalidate). From VERIFIED, demotes back to NOT_READY
     * (Revision 4 §4's own explicit "VERIFIED -&gt; NOT_READY only when verification detects a
     * mismatch" transition). From CANONICAL_ENABLED or later, enters CANONICAL_DATA_INVALID — the
     * automatic safety-state entry (frozen implementation instruction §1) — legacy remains
     * available as the fallback for every booking-adjacent surface; this never hard-fails
     * bookings. Already-CANONICAL_DATA_INVALID stays CANONICAL_DATA_INVALID.
     */
    @Transactional
    public void markInvalid(UUID businessId, List<String> violations) {
        Business business = lockBusiness(businessId);
        BookingCutoverState current = business.getBookingCutoverState();
        switch (current) {
            case NOT_READY -> {
                // Nothing to invalidate — was never verified in the first place.
            }
            case VERIFIED -> writeState(business, BookingCutoverState.NOT_READY);
            case CANONICAL_ENABLED, LEGACY_RETIREMENT_READY, LEGACY_RETIRED -> {
                writeState(business, BookingCutoverState.CANONICAL_DATA_INVALID);
                logActivity(businessId, "Booking canonical data marked INVALID: " + String.join("; ", violations));
            }
            case CANONICAL_DATA_INVALID -> {
                // Already invalid — idempotent, but still worth a log entry: a second, independent
                // defect surfaced while the first was never resolved.
                logActivity(businessId, "Booking canonical data verification found additional violations while already CANONICAL_DATA_INVALID: "
                        + String.join("; ", violations));
            }
        }
    }

    /** Requires VERIFIED. Explicit operator action — enablement is never implied by verification alone. */
    @Transactional
    public void enableCanonical(UUID businessId) {
        Business business = lockBusiness(businessId);
        if (business.getBookingCutoverState() != BookingCutoverState.VERIFIED) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "enableCanonical requires VERIFIED (current: " + business.getBookingCutoverState() + ").");
        }
        writeState(business, BookingCutoverState.CANONICAL_ENABLED);
    }

    /** Requires CANONICAL_ENABLED. Pure deployment toggle — verification state is never lost by this call. */
    @Transactional
    public void revertToVerified(UUID businessId) {
        Business business = lockBusiness(businessId);
        if (business.getBookingCutoverState() != BookingCutoverState.CANONICAL_ENABLED) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "revertToVerified requires CANONICAL_ENABLED (current: " + business.getBookingCutoverState() + ").");
        }
        writeState(business, BookingCutoverState.VERIFIED);
    }

    /**
     * Requires CANONICAL_DATA_INVALID and a clean, full re-verification. Recovery ends at VERIFIED
     * only — re-enabling canonical serving requires a SEPARATE, subsequent {@link #enableCanonical}
     * call (Revision 4 §1 — "CANONICAL_DATA_INVALID must never transition directly to
     * CANONICAL_ENABLED").
     */
    @Transactional
    public void recoverFromInvalid(UUID businessId, VerificationResult result) {
        Business business = lockBusiness(businessId);
        if (business.getBookingCutoverState() != BookingCutoverState.CANONICAL_DATA_INVALID) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "recoverFromInvalid requires CANONICAL_DATA_INVALID (current: " + business.getBookingCutoverState() + ").");
        }
        if (!result.clean()) {
            throw new IllegalArgumentException("recoverFromInvalid requires a clean VerificationResult.");
        }
        writeState(business, BookingCutoverState.VERIFIED);
    }

    /**
     * The single, reusable translation from "a verification run just finished" to "what the
     * lifecycle should do about it" — used by both {@code BookingCutoverController.verify()} and
     * this project's own tests, so there is exactly one place this outcome-application logic
     * lives (never duplicated at each call site). Clean -&gt; markVerified/recoverFromInvalid as
     * appropriate for the current state; not clean -&gt; markInvalid (a no-op from NOT_READY).
     */
    @Transactional
    public void applyVerificationOutcome(UUID businessId, VerificationResult result) {
        BookingCutoverState current = resolve(businessId);
        if (result.clean()) {
            if (current == BookingCutoverState.CANONICAL_DATA_INVALID) {
                recoverFromInvalid(businessId, result);
            } else {
                markVerified(businessId, result);
            }
        } else if (current != BookingCutoverState.NOT_READY) {
            List<String> violations = result.mismatches().stream()
                    .map(m -> m.code() + " " + m.subjectType() + " " + m.subjectId() + ": " + m.detail())
                    .toList();
            markInvalid(businessId, violations);
        }
        // else: already NOT_READY and still not clean — nothing to transition.
    }

    private Business lockBusiness(UUID businessId) {
        return businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
    }

    private void writeState(Business business, BookingCutoverState newState) {
        BookingCutoverState previous = business.getBookingCutoverState();
        business.setBookingCutoverState(newState);
        businessRepository.save(business);
        logActivity(business.getId(), "Booking cutover state changed: " + previous + " -> " + newState);
    }

    // Uses the low-level, explicit-businessId ActivityLogService overload deliberately, never the
    // TenantContext-reading convenience wrapper: every method on this resolver takes businessId as
    // an explicit parameter (matching BookingService's own "public booking paths carry no JWT, so
    // TenantContext is never assumed populated" discipline) and must remain callable the same way
    // from a real-Postgres integration test with no HTTP request/JWT in play at all.
    private void logActivity(UUID businessId, String message) {
        activityLogService.log(businessId, TenantContext.getUserId(), message, "BUSINESS", businessId);
    }
}
