package com.ratel.rbms.dto;

public record StartHubConfigResponse(
        String businessName,
        boolean bookingEnabled,
        boolean customOrderEnabled,
        // The business has an active points program, so customers can check their points by phone number.
        boolean pointsEnabled,
        String businessWhatsappLink
) {
}
