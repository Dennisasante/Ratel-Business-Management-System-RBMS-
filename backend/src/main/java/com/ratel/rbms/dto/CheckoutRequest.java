package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CheckoutRequest(
        @NotNull(message = "A plan must be selected")
        UUID planId,

        // Nullable: absent/false means "just this one payment" — auto-renew
        // is an explicit opt-in, not implied by paying with a card.
        Boolean saveCard
) {
}
