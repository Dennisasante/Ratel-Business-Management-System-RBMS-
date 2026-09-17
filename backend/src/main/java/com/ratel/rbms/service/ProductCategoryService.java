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
                .map(c -> ProductCategoryResponse.from(
                        c,
                        productRepository.countByBusinessIdAndCategoryId(businessId, c.getId()),
                        productCategoryRepository.countByBusinessIdAndParentId(businessId, c.getId())))
                .toList();
    }

    public ProductCategoryResponse create(ProductCategoryRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        if (productCategoryRepository.existsByBusinessIdAndNameIgnoreCase(businessId, req.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "A category with this name already exists.");
        }
        UUID parentId = validatedParentId(businessId, req.parentId());

        ProductCategory category = ProductCategory.builder()
                .businessId(businessId)
                .name(req.name())
                .parentId(parentId)
                .build();
        category = productCategoryRepository.save(category);

        activityLogService.log("Added product category \"" + category.getName() + "\"", "PRODUCT_CATEGORY", category.getId());

        return ProductCategoryResponse.from(category, 0, 0);
    }

    // Renaming — and now re-parenting — is instant, nothing snapshots a category name/parent
    // the way sale/PO line items snapshot a product name.
    public ProductCategoryResponse rename(UUID id, ProductCategoryRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        ProductCategory category = getOwned(id);

        if (!category.getName().equalsIgnoreCase(req.name())
                && productCategoryRepository.existsByBusinessIdAndNameIgnoreCase(businessId, req.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "A category with this name already exists.");
        }
        UUID parentId = validatedParentId(businessId, req.parentId());
        if (parentId != null && parentId.equals(id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A category can't be its own subcategory.");
        }
        // Turning a category WITH its own subcategories into a subcategory of something else
        // would create two-level nesting — blocked, same as create() blocking a subcategory
        // parent from itself having a parent.
        if (parentId != null && productCategoryRepository.countByBusinessIdAndParentId(businessId, id) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "This category has its own subcategories — move or remove them first.");
        }

        category.setName(req.name());
        category.setParentId(parentId);
        category = productCategoryRepository.save(category);

        long productCount = productRepository.countByBusinessIdAndCategoryId(businessId, category.getId());
        long subcategoryCount = productCategoryRepository.countByBusinessIdAndParentId(businessId, category.getId());
        return ProductCategoryResponse.from(category, productCount, subcategoryCount);
    }

    // Blocked (not auto-uncategorized) while any product still points at it, or while any
    // subcategory still points at it as parent — silently uncategorizing/orphaning would hide
    // a data problem, per the spec's confirmed decision (already applied to products; extended
    // here to subcategories rather than ever relying on a DB-level cascade delete).
    public void delete(UUID id) {
        UUID businessId = TenantContext.getBusinessId();
        ProductCategory category = getOwned(id);

        long productCount = productRepository.countByBusinessIdAndCategoryId(businessId, id);
        if (productCount > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    productCount + " product" + (productCount == 1 ? "" : "s") + " use" + (productCount == 1 ? "s" : "") + " this — reassign them first.");
        }
        long subcategoryCount = productCategoryRepository.countByBusinessIdAndParentId(businessId, id);
        if (subcategoryCount > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    subcategoryCount + " subcategor" + (subcategoryCount == 1 ? "y" : "ies") + " use" + (subcategoryCount == 1 ? "s" : "") + " this — remove them first.");
        }

        productCategoryRepository.delete(category);
        activityLogService.log("Removed product category \"" + category.getName() + "\"", "PRODUCT_CATEGORY", id);
    }

    // Shared by create() and rename(): a null parentId is always fine (top-level category);
    // a non-null one must reference a real category owned by this business that does NOT
    // itself have a parent — the single rule that keeps nesting to exactly one level.
    private UUID validatedParentId(UUID businessId, UUID parentId) {
        if (parentId == null) return null;
        ProductCategory parent = productCategoryRepository.findByIdAndBusinessId(parentId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Parent category not found."));
        if (parent.getParentId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A subcategory can't itself have subcategories.");
        }
        return parentId;
    }

    private ProductCategory getOwned(UUID id) {
        return productCategoryRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found."));
    }
}
