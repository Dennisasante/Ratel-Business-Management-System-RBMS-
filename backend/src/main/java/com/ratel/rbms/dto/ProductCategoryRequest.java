package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ProductCategoryRequest(
        @NotBlank(message = "Category name is required")
        String name,
        // Null for a top-level category. Set to make this a subcategory of an existing
        // top-level category — that parent must not itself already have a parent, since
        // nesting only ever goes one level deep.
        UUID parentId
) {
}
