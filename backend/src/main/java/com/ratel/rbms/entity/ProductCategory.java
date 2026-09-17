package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_categories", uniqueConstraints = {
        @UniqueConstraint(name = "uq_product_categories_business_name", columnNames = {"business_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCategory {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false, length = 100)
    private String name;

    // Null for a top-level category; set for a subcategory. Never itself points at a row
    // that also has a parentId — single-level nesting only, enforced in ProductCategoryService.
    @Column(name = "parent_id")
    private UUID parentId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
