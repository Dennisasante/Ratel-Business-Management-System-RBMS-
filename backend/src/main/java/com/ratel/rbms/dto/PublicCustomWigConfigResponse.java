package com.ratel.rbms.dto;

import java.util.List;
import java.util.UUID;

public record PublicCustomWigConfigResponse(
        UUID businessId,
        String businessName,
        boolean enabled,
        String currency,
        List<CustomItemAttributeResponse> attributes,
        String businessWhatsappLink
) {
}
