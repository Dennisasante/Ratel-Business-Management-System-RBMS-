package com.ratel.rbms.dto;

/** What the public points page shows before a customer enters their number. */
public record PublicLoyaltyBusinessResponse(String businessName, String logoUrl, int targetPoints, String rewardDescription) {
}
