package com.ratel.rbms.dto;

import com.ratel.rbms.entity.AiSettings;

public record AiSettingsResponse(
        boolean active,
        String agentName,
        String greeting,
        String tone,
        String systemInstructions,
        boolean humanHandoffEnabled,
        String humanHandoffMessage
) {
    public static AiSettingsResponse from(AiSettings s) {
        return new AiSettingsResponse(
                s.isActive(), s.getAgentName(), s.getGreeting(), s.getTone(),
                s.getSystemInstructions(), s.isHumanHandoffEnabled(), s.getHumanHandoffMessage()
        );
    }

    // Sane defaults for a business that's had the AI module turned on but
    // has never saved settings yet — the frontend still gets a well-formed
    // object to render a form against, not a null.
    public static AiSettingsResponse defaults() {
        return new AiSettingsResponse(true, "Tallia", null, null, null, true, null);
    }
}
