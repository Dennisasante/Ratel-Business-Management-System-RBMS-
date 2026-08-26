package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiSettingsUpdateRequest(
        boolean active,

        @NotBlank(message = "Give your AI agent a name")
        @Size(max = 80)
        String agentName,

        String greeting,
        String tone,

        @Size(max = 8000, message = "Keep system instructions under 8000 characters")
        String systemInstructions,

        boolean humanHandoffEnabled,
        String humanHandoffMessage
) {
}
