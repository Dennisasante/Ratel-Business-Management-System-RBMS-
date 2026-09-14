package com.ratel.rbms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
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
        String customerLocation,

        // Phase 4 — nullable. Opened via a prior PolicyEngine.beginCommitment() call (Layer-A
        // pre-flight, e.g. after showing/acknowledging any required policy). BookingService
        // passes this into PolicyEngine.evaluateGate(); its absence only blocks the booking if
        // this business actually has an applicable, blocking policy configured for BOOKING_CREATE.
        UUID commitmentReference,

        // Restaurant-AI-demo phase — both nullable/additive, unused by every existing caller
        // (public widget, staff form, every pre-existing AI booking). Only meaningful for a
        // PACKAGE booking under CANONICAL_ENABLED: `selections` maps a PackageComponent id to the
        // Option id chosen for it (componentId -> optionId, both UUIDs); a component omitted here
        // falls back to its own default option, exactly matching PackagePricingService's own
        // "omission implies default" contract (Phase 5B, unmodified). `partySize` multiplies the
        // resulting per-guest PackagePricingService total — a plain integer multiplication done in
        // BookingService itself, never by the AI/LLM. Both null/empty preserves EXACTLY today's
        // existing canonical-package behavior (single default-configuration instance, party size 1).
        Map<UUID, UUID> selections,

        Integer partySize
) {
}
