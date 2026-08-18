package com.ratel.rbms.exception;

import java.util.UUID;

// Thrown by a domain service (SaleService/ServiceOrderService/CustomWigRequestService)
// when a non-Owner attempts a price edit or refund — the change was queued as a
// PendingApproval instead of applied. Caught by GlobalExceptionHandler and turned
// into a 202 Accepted, not an error: the request succeeded, it's just not applied yet.
public class ApprovalRequiredException extends RuntimeException {

    private final UUID pendingApprovalId;

    public ApprovalRequiredException(UUID pendingApprovalId, String message) {
        super(message);
        this.pendingApprovalId = pendingApprovalId;
    }

    public UUID getPendingApprovalId() {
        return pendingApprovalId;
    }
}
