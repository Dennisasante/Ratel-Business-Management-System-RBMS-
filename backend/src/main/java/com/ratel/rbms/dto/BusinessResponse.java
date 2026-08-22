package com.ratel.rbms.dto;

import com.ratel.rbms.entity.Business;

import java.util.List;
import java.util.UUID;

public record BusinessResponse(
        UUID id,
        String name,
        String slug,
        String industry,
        String location,
        String contactEmail,
        String contactPhone,
        String currency,
        String subscriptionPlan,
        List<String> enabledModules,
        String logoUrl,
        String signatureUrl,
        String taxId,
        // Every business-scoped user (not just Owners) needs to know this, so
        // the read-only banner shows up for staff too — that's why it rides
        // along here rather than only on the Owner-restricted /billing/status.
        String billingStatus,
        // The current plan's feature codes (BOOKING_WIDGET, WOOCOMMERCE_SYNC,
        // CUSTOM_WIG_REQUESTS) — rides along here, not just the Owner-only
        // /billing/status, so Managers can also know what to show on the
        // dashboard without a second, role-gated fetch.
        List<String> planFeatures
) {
    public static BusinessResponse from(Business b, List<String> planFeatures) {
        return new BusinessResponse(
                b.getId(),
                b.getName(),
                b.getSlug(),
                b.getIndustry().name(),
                b.getLocation(),
                b.getContactEmail(),
                b.getContactPhone(),
                b.getCurrency(),
                b.getSubscriptionPlan(),
                b.getEnabledModules(),
                b.getLogoUrl(),
                b.getSignatureUrl(),
                b.getTaxId(),
                b.getBillingStatus().name(),
                planFeatures
        );
    }
}
