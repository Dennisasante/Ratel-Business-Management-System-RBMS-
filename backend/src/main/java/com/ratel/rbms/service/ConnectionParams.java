package com.ratel.rbms.service;

import java.util.Map;

/**
 * Talia Unified Platform, Phase 5D Stage 0 — the generic input to
 * {@link ChannelConnectionInitiator#beginConnection}. Deliberately a plain string map, not a
 * typed object: CORE/AI never know a channel's field names (a WhatsApp phone-number-ID, an
 * Instagram account id, a future provider's own shape) — only the channel-specific
 * {@link ChannelConnectionInitiator} implementation interprets {@code values}. Typed validation
 * (e.g. {@code @NotBlank}) belongs on the controller-facing request DTO for each channel, which
 * converts into this generic shape before calling the business-facing
 * {@code AiChannelConnectionService}.
 */
public record ConnectionParams(Map<String, String> values) {

    public String get(String key) {
        return values == null ? null : values.get(key);
    }
}
