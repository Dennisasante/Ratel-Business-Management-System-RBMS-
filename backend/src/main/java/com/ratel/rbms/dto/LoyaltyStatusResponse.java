package com.ratel.rbms.dto;

/** One customer's standing: what the cashier sees at the till and what the receipt text uses. */
public record LoyaltyStatusResponse(
        // False when the business has no active program (module off or program switched off).
        boolean active,
        // Visits counted toward the NEXT reward (can exceed the target when rewards are waiting).
        int visits,
        int target,
        // The same standing in points: visits x points-per-visit, and the target x points-per-visit.
        int points,
        int targetPoints,
        int pointsPerVisit,
        // A visit was already counted for this customer today (only one per day), so another sale today adds nothing.
        boolean visitedToday,
        boolean rewardDue,
        int rewardsDue,
        String rewardDescription,
        // The customer's personal points page, e.g. https://app.example.com/p/k3x9a2f81m. Null until their first visit.
        String pointsUrl
) {
}
