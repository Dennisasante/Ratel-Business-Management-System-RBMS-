package com.ratel.rbms.dto;

import com.ratel.rbms.entity.ProductCategory;

import java.time.Instant;
import java.util.UUID;

public record ProductCategoryResponse(
        UUID id,
        String name,
        long productCount,
        Instant createdAt
) {
    public static ProductCategoryResponse from(ProductCategory category, long productCount) {
        return new ProductCategoryResponse(category.getId(), category.getName(), productCount, category.getCreatedAt());
    }
}
