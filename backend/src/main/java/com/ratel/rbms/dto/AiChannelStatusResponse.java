package com.ratel.rbms.dto;

/**
 * One row of the dashboard's read-only "Channels" section (spec §27) — never
 * exposes a binding's credentials or any other sensitive field, just enough
 * to show what's connected. WEB_DEMO is always connected once the AI module
 * itself is on; every other channel is "not connected" until a real binding
 * exists, which nothing in this phase ever creates.
 */
public record AiChannelStatusResponse(
        String channel,
        String label,
        boolean connected,
        String statusMessage
) {
}
