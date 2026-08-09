package com.ratel.rbms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateBookingRequest(
        // Exactly one of serviceCatalogId/packageId must be set — validated in
        // BookingService.createBooking() since "exactly one of two optional
        // fields" isn't expressible with bean validation annotations alone.
        UUID serviceCatalogId,

        UUID packageId,

        @NotBlank(message = "Your name is required")
        String customerName,

        @NotBlank(message = "Your email is required")
        @Email(message = "Enter a valid email")
        String customerEmail,

        @NotBlank(message = "A WhatsApp number is required")
        String customerWhatsapp,

        @NotNull(message = "Choose a date and time")
        Instant scheduledAt,

        String notes,

        // Required only when the resolved service/package has requiresLocation set.
        String customerLocation
) {
}
