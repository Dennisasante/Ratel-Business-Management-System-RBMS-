package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

public record RespondHelpRequestRequest(
        @NotBlank String response
) {
}
