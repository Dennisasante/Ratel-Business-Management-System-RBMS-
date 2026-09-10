package com.ratel.rbms.dto;

import java.util.UUID;

// Layer-A / pre-flight only (Revision 4 §5) — what a staff UI shows before submitting a
// transaction. Never authoritative; PolicyEngine.evaluateGate() is re-run at persistence time
// regardless of what this endpoint returned.
public record PolicySummaryResponse(
        UUID policyId,
        String policyKey,
        String appliesToAction,
        UUID offeringId,
        int versionNumber,
        String title,
        String content,
        boolean requiresAcknowledgement,
        boolean blocksTransaction,
        boolean staffOverridable
) {
}
