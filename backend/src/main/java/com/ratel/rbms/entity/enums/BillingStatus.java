package com.ratel.rbms.entity.enums;

public enum BillingStatus {
    TRIALING,
    ACTIVE,
    // A lapsed paid subscription lands here for a 7-day window (see
    // Business.gracePeriodEndsAt) before falling through to READ_ONLY —
    // ReadOnlyEnforcementFilter only blocks on READ_ONLY, so a business in
    // GRACE stays fully functional. Trial expiry skips this and goes
    // straight to READ_ONLY; nobody asked to change that behavior.
    GRACE,
    READ_ONLY
}
