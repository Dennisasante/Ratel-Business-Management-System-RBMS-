package com.ratel.rbms.dto;

public record StartHubConfigResponse(
        String businessName,
        boolean bookingEnabled,
        boolean customOrderEnabled,
        String businessWhatsappLink
) {
}
