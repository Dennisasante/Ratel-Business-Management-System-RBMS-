package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateSlugRequest(
        @NotBlank(message = "A booking page link is required")
        String slug
) {
}
