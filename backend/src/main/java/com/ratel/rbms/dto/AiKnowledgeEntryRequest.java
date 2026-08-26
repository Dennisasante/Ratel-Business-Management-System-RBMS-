package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiKnowledgeEntryRequest(
        @NotBlank(message = "Give this entry a title")
        @Size(max = 200)
        String title,

        @NotBlank(message = "Content is required")
        String content,

        // Suggested: FAQ, BUSINESS_INFO, SERVICE, POLICY, RESTAURANT, HOTEL,
        // EVENTS, BEACH, OTHER — plain string, not enforced against a fixed
        // list server-side (same posture as every other varchar "category"
        // column in this codebase, e.g. ProductCategory).
        String category,

        boolean active
) {
}
