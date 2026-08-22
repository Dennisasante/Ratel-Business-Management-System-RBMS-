package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PlatformBusinessModulesUpdateRequest(
        @NotNull(message = "Enabled modules list is required")
        List<String> enabledModules
) {
}
