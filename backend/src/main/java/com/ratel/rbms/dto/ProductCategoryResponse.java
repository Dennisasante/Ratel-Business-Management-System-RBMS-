package com.ratel.rbms.dto;

import com.ratel.rbms.entity.ProductCategory;

import java.time.Instant;
import java.util.UUID;

public record ProductCategoryResponse(
        UUID id,
        String name,
        UUID parentId,
        long productCount,
        long subcategoryCount,
        Instant createdAt
) {
    public static ProductCategoryResponse from(ProductCategory category, long productCount, long subcategoryCount) {
        return new ProductCategoryResponse(
                category.getId(), category.getName(), category.getParentId(), productCount, subcategoryCount, category.getCreatedAt());
    }
}
