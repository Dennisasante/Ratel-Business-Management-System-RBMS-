package com.ratel.rbms.service;

import java.util.List;

/**
 * Talia Unified Platform, Phase 4 — the outcome of {@link PolicyEngine#evaluateGate}: the
 * single authoritative ALLOW/BLOCK decision immediately before a transaction is persisted
 * (Revision 4 §5, "Final Gate Contract"). Pre-flight/UX checks ({@code applicablePolicies},
 * {@code unsatisfiedRequiredPolicies}) are advisory only — this is the one result a transaction
 * service is allowed to trust.
 */
public record GateResult(boolean allowed, List<String> reasons) {

    public static GateResult allow() {
        return new GateResult(true, List.of());
    }

    public static GateResult block(String reason) {
        return new GateResult(false, List.of(reason));
    }

    public static GateResult block(List<String> reasons) {
        return new GateResult(false, List.copyOf(reasons));
    }
}
