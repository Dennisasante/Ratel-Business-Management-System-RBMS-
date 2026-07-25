package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findAllByBusinessIdOrderByNameAsc(UUID businessId);

    Optional<Product> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndSku(UUID businessId, String sku);

    boolean existsByBusinessIdAndCategoryId(UUID businessId, UUID categoryId);

    long countByBusinessIdAndCategoryId(UUID businessId, UUID categoryId);

    // Backs the Inventory search box + category filter, and the identical params
    // on the Sales/PO product pickers. includeArchived=false is the default view;
    // true (or an explicit categoryId) still respects the other filters.
    // :search is cast explicitly — otherwise Postgres can't infer a type for the bind
    // parameter when it's null (the "no filter" case) and LOWER() fails with
    // "function lower(bytea) does not exist".
    @Query("SELECT p FROM Product p WHERE p.businessId = :businessId AND "
            + "(:includeArchived = true OR p.active = true) AND "
            + "(:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) "
            + "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) AND "
            + "(:categoryId IS NULL OR p.categoryId = :categoryId) "
            + "ORDER BY p.name ASC")
    List<Product> search(
            @Param("businessId") UUID businessId,
            @Param("search") String search,
            @Param("categoryId") UUID categoryId,
            @Param("includeArchived") boolean includeArchived
    );
}
