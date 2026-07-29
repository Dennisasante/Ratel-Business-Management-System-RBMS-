package com.ratel.rbms.dto;

import com.ratel.rbms.entity.Business;

import java.util.List;
import java.util.UUID;

public record BusinessResponse(
        UUID id,
        String name,
        String industry,
        String location,
        String contactEmail,
        String contactPhone,
        String currency,
        String subscriptionPlan,
        List<String> enabledModules,
        String logoUrl,
        // Every business-scoped user (not just Owners) needs to know this, so
        // the read-only banner shows up for staff too — that's why it rides
        // along here rather than only on the Owner-restricted /billing/status.
        String billingStatus
) {
    public static BusinessResponse from(Business b) {
        return new BusinessResponse(
                b.getId(),
                b.getName(),
                b.getIndustry().name(),
                b.getLocation(),
                b.getContactEmail(),
                b.getContactPhone(),
                b.getCurrency(),
                b.getSubscriptionPlan(),
                b.getEnabledModules(),
                b.getLogoUrl(),
                b.getBillingStatus().name()
        );
    }
}
