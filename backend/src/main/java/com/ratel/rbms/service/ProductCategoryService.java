package com.ratel.rbms.service;

import com.ratel.rbms.dto.ProductCategoryRequest;
import com.ratel.rbms.dto.ProductCategoryResponse;
import com.ratel.rbms.entity.ProductCategory;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.ProductCategoryRepository;
import com.ratel.rbms.repository.ProductRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProductCategoryService {

    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final ActivityLogService activityLogService;

    public ProductCategoryService(
            ProductCategoryRepository productCategoryRepository,
            ProductRepository productRepository,
            ActivityLogService activityLogService
    ) {
        this.productCategoryRepository = productCategoryRepository;
        this.productRepository = productRepository;
        this.activityLogService = activityLogService;
    }

    public List<ProductCategoryResponse> listAll() {
        UUID businessId = TenantContext.getBusinessId();
        return productCategoryRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .map(c -> ProductCategoryResponse.from(c, productRepository.countByBusinessIdAndCategoryId(businessId, c.getId())))
                .toList();
    }

    public ProductCategoryResponse create(ProductCategoryRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        if (productCategoryRepository.existsByBusinessIdAndNameIgnoreCase(businessId, req.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "A category with this name already exists.");
        }

        ProductCategory category = ProductCategory.builder()
                .businessId(businessId)
                .name(req.name())
                .build();
        category = productCategoryRepository.save(category);

        activityLogService.log("Added product category \"" + category.getName() + "\"", "PRODUCT_CATEGORY", category.getId());

        return ProductCategoryResponse.from(category, 0);
    }

    // Renaming is instant — it's just relabeling, nothing snapshots a category name
    // the way sale/PO line items snapshot a product name.
    public ProductCategoryResponse rename(UUID id, ProductCategoryRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        ProductCategory category = getOwned(id);

        if (!category.getName().equalsIgnoreCase(req.name())
                && productCategoryRepository.existsByBusinessIdAndNameIgnoreCase(businessId, req.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "A category with this name already exists.");
        }

        category.setName(req.name());
        category = productCategoryRepository.save(category);

        long productCount = productRepository.countByBusinessIdAndCategoryId(businessId, category.getId());
        return ProductCategoryResponse.from(category, productCount);
    }

    // Blocked (not auto-uncategorized) while any product still points at it — silently
    // uncategorizing would hide a data problem, per the spec's confirmed decision.
    public void delete(UUID id) {
        UUID businessId = TenantContext.getBusinessId();
        ProductCategory category = getOwned(id);

        long productCount = productRepository.countByBusinessIdAndCategoryId(businessId, id);
        if (productCount > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    productCount + " product" + (productCount == 1 ? "" : "s") + " use" + (productCount == 1 ? "s" : "") + " this — reassign them first.");
        }

        productCategoryRepository.delete(category);
        activityLogService.log("Removed product category \"" + category.getName() + "\"", "PRODUCT_CATEGORY", id);
    }

    private ProductCategory getOwned(UUID id) {
        return productCategoryRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found."));
    }
}
