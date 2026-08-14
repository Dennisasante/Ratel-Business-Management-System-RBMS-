package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CheckoutRequest(
        @NotNull(message = "A plan must be selected")
        UUID planId,

        // Nullable: absent means "1 month" — the pre-existing single-cycle
        // behavior, so any older caller of this endpoint keeps working
        // unchanged. Must be 1, 3, 6, or 12 — validated in
        // BillingService.discountForMonths(), not here.
        Integer months,

        // Nullable: absent/false means "just this one payment" — auto-renew
        // is an explicit opt-in, not implied by paying with a card.
        Boolean saveCard
) {
}
