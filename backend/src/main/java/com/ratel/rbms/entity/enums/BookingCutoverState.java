package com.ratel.rbms.entity.enums;

/**
 * Talia Unified Platform, Phase 5C — the explicit per-business Booking cutover lifecycle
 * (frozen design, Revision 4 §4). Deliberately NOT derived from "did verification last pass" —
 * verification fact and deployment/enablement fact are two separate things, per the same
 * revision's own corrective HOLD. See {@code service/BookingCutoverStateResolver.java} for the
 * sole authorized reader/writer of this state.
 *
 * <p>{@link #LEGACY_RETIREMENT_READY}/{@link #LEGACY_RETIRED} are modeled here for lifecycle
 * completeness only — no Phase 5C operator flow transitions a business into either state
 * (Revision 4 §6). {@code BookingCutoverStateResolver.useCanonical} treats them identically to
 * {@link #CANONICAL_ENABLED} purely so the lifecycle graph stays internally consistent if a future
 * phase activates them.
 */
public enum BookingCutoverState {
    /** Default. Every booking-adjacent read/write uses legacy exclusively. */
    NOT_READY,
    /** Stage 1 offline verification has passed. Still legacy — enablement is a separate, explicit act. */
    VERIFIED,
    /** Canonical is authoritative for this business's booking pricing/eligibility/reads. */
    CANONICAL_ENABLED,
    /**
     * A live verification/integrity check found a real defect after this business reached
     * VERIFIED or later. Every booking-adjacent surface falls back to legacy (never a hard
     * failure) until an operator-triggered full re-verification passes and a separate,
     * explicit re-enable action is taken.
     */
    CANONICAL_DATA_INVALID,
    /** Dormant in Phase 5C — modeled for lifecycle completeness only, never activated. */
    LEGACY_RETIREMENT_READY,
    /** Dormant in Phase 5C — modeled for lifecycle completeness only, never activated. */
    LEGACY_RETIRED
}
