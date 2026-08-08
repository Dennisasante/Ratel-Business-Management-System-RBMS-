package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;

import java.util.List;

public record CustomItemAttributeRequest(
        @NotBlank(message = "Attribute name is required")
        String name,

        Integer sortOrder,

        // Full replacement — saving an attribute rewrites its whole option list,
        // same "delete then rebuild from the fresh payload" approach used for
        // e-commerce order line items. Simpler than diffing, and pricing rules
        // are edited as a whole set in the UI anyway.
        @NotEmpty(message = "Add at least one option")
        List<@Valid CustomItemAttributeOptionRequest> options
) {
}
