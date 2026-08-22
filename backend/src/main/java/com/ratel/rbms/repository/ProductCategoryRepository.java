package com.ratel.rbms.repository;

import com.ratel.rbms.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    List<ProductCategory> findAllByBusinessIdOrderByNameAsc(UUID businessId);

    Optional<ProductCategory> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

    Optional<ProductCategory> findByBusinessIdAndNameIgnoreCase(UUID businessId, String name);
}
