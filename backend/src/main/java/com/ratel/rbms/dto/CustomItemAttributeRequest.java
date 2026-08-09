package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;

import java.util.List;

public record CustomItemAttributeRequest(
        @NotBlank(message = "Attribute name is required")
        String name,

        Integer sortOrder,

        // "SINGLE" or "MULTIPLE" — null defaults to SINGLE in the service.
        String selectionType,

        // Optional grouping label for the hosted configurator page — attributes
        // sharing the same stepGroup render together as one step. Null/blank
        // means this attribute gets its own step.
        String stepGroup,

        // Full replacement — saving an attribute rewrites its whole option list,
        // same "delete then rebuild from the fresh payload" approach used for
        // e-commerce order line items. Simpler than diffing, and pricing rules
        // are edited as a whole set in the UI anyway.
        @NotEmpty(message = "Add at least one option")
        List<@Valid CustomItemAttributeOptionRequest> options
) {
}
