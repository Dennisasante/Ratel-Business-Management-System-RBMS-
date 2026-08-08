package com.ratel.rbms.dto;

import java.util.List;

public record PublicCustomWigConfigResponse(
        String businessName,
        boolean enabled,
        String currency,
        List<CustomItemAttributeResponse> attributes,
        String businessWhatsappLink
) {
}
