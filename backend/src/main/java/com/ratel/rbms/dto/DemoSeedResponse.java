package com.ratel.rbms.dto;

import java.util.UUID;

public record DemoSeedResponse(
        UUID businessId,
        String slug,
        String ownerEmail,
        String ownerPassword,
        boolean created
) {
}
