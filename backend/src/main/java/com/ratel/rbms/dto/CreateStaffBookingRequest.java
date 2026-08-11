package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateStaffBookingRequest(
        // Exactly one of serviceCatalogId/packageId must be set — validated in
        // BookingService.createStaffBooking(), same "not expressible via bean
        // validation alone" reasoning as CreateBookingRequest.
        UUID serviceCatalogId,

        UUID packageId,

        // Nullable: pick an existing customer, or fall through to the raw
        // fields below (which upsert-by-phone the same way the public widget
        // does). Neither is required — a call-in with no phone on hand just
        // gets a name, matching ServiceOrderForm's "no customer on file" allowance.
        UUID customerId,

        String customerName,

        String customerEmail,

        String customerWhatsapp,

        @NotNull(message = "Choose a date and time")
        Instant scheduledAt,

        String notes,

        // Required only when the resolved service/package has requiresLocation set.
        String customerLocation,

        // Nullable: tracking only, can be assigned later.
        UUID assignedStaffId,

        // Staff set this directly — no Paystack step for a booking taken over
        // the phone. UNPAID, PAID, or PAY_IN_PERSON (not FAILED — that's a
        // payment-attempt outcome, not a choice staff make).
        @NotBlank(message = "Payment status is required")
        String paymentStatus
) {
}
