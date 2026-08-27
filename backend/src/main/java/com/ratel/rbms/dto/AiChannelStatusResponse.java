package com.ratel.rbms.dto;

import java.time.Instant;

/**
 * One row of the dashboard's read-only "Channels" section (spec §27/§30) —
 * never exposes a binding's credentials or any other sensitive field, just
 * enough to show what's connected. WEB_DEMO is always connected once the AI
 * module itself is on. For WhatsApp specifically, phoneNumberId/displayName/
 * updatedAt are populated once a Super Admin has configured a binding (see
 * WhatsAppBindingService) — active distinguishes "configured but paused"
 * from "configured and live," which connected alone can't express.
 */
public record AiChannelStatusResponse(
        String channel,
        String label,
        boolean connected,
        String statusMessage,
        boolean active,
        String phoneNumberId,
        String displayName,
        Instant updatedAt
) {
    public static AiChannelStatusResponse webDemo() {
        return new AiChannelStatusResponse("WEB_DEMO", "Web Demo", true, "Connected", true, null, null, null);
    }

    public static AiChannelStatusResponse notImplemented(String channel, String label) {
        return new AiChannelStatusResponse(channel, label, false, "Not connected — not yet implemented", false, null, null, null);
    }
}
