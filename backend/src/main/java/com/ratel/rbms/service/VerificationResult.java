package com.ratel.rbms.service;

import java.util.List;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5C — the outcome of one Stage 1 offline verification run for one
 * business (frozen design, Revision 4 §1/§3). {@code clean} is true only when {@code mismatches}
 * is empty — commercial parity AND policy-narrowing are both zero-mismatch requirements;
 * {@code policyWidenings} is informational only and never affects {@code clean}
 * (Revision 4 §1 — the fix for the Revision 2 HOLD conflict between commercial parity and
 * intentional policy widening).
 */
public record VerificationResult(UUID businessId, List<ParityMismatch> mismatches, List<String> policyWidenings) {

    public boolean clean() {
        return mismatches.isEmpty();
    }
}
