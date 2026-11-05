package com.ratel.rbms.dto;

import java.util.UUID;

public record LoyaltyCustomerRowResponse(
        UUID customerId,
        String customerName,
        String phone,
        int visits,
        int target,
        boolean rewardDue,
        int rewardsDue,
        int lifetimeVisits
) {
}
