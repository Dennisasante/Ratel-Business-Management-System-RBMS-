package com.ratel.rbms.dto;

/** One customer's standing: what the cashier sees at the till and what the receipt text uses. */
public record LoyaltyStatusResponse(
        // False when the business has no active program (module off or program switched off).
        boolean active,
        // Visits counted toward the NEXT reward (can exceed the target when rewards are waiting).
        int visits,
        int target,
        boolean rewardDue,
        int rewardsDue,
        String rewardDescription,
        // The customer's personal points page, e.g. https://app.example.com/p/k3x9a2f81m. Null until their first visit.
        String pointsUrl
) {
}
