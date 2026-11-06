package com.ratel.rbms.dto;

/** What a customer sees on their personal points page. Deliberately minimal — no ids, no phone numbers. */
public record PublicLoyaltyResponse(
        String businessName,
        String logoUrl,
        String customerFirstName,
        int visits,
        int target,
        int points,
        int targetPoints,
        boolean rewardDue,
        String rewardDescription,
        // Path of the business's online booking page, e.g. /book/glow-salon; null when they don't take bookings online.
        String bookingPath
) {
}
