package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Restaurant-AI-demo phase — the authoritative preview of what createBooking's own selections/
 * partySize would actually charge, computed the exact same way (PackagePricingService.calculate,
 * then a plain Java partySize multiplication) so an AI tool can quote a real total before
 * booking, never invent or estimate one itself.
 */
public record PackagePricingPreviewResponse(
        UUID packageId,
        BigDecimal perGuestPrice,
        int partySize,
        BigDecimal totalPrice,
        // Restaurant-AI-demo phase — the standard 70% deposit / 30% balance split this phase's
        // own reservation policy specifies for event/package bookings (see the seeded
        // "Confirmation of Reservation" policy content). Deliberately independent of
        // BusinessIntegrations.bookingDepositPercent, which is a different, Paystack-gated,
        // per-business setting for ordinary (non-package) bookings — computed here so the AI
        // never has to do this arithmetic itself when narrating a simulated deposit.
        BigDecimal depositAmount,
        BigDecimal balanceAmount,
        List<PricingLine> lines
) {
    public record PricingLine(String label, BigDecimal perGuestAmount) {
    }
}
