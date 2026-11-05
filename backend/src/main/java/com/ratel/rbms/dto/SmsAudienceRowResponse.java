package com.ratel.rbms.dto;

import java.util.UUID;

/** One customer in the campaign picker. */
public record SmsAudienceRowResponse(
        UUID id,
        String name,
        String phone,
        boolean optedOut,
        // Has a usable phone number and hasn't opted out — i.e. would actually receive a campaign.
        boolean reachable
) {
}
