package com.ratel.rbms.service;

import com.ratel.rbms.dto.ProductRequest;
import com.ratel.rbms.dto.StockAdjustmentRequest;
import com.ratel.rbms.entity.Product;
import com.ratel.rbms.entity.StockMovement;
import com.ratel.rbms.entity.enums.MovementType;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.ProductRepository;
import com.ratel.rbms.repository.StockMovementRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ActivityLogService activityLogService;

    public ProductService(
            ProductRepository productRepository,
            StockMovementRepository stockMovementRepository,
            ActivityLogService activityLogService
    ) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.activityLogService = activityLogService;
    }

    public List<Product> listAll() {
        return productRepository.findAllByBusinessIdOrderByNameAsc(TenantContext.getBusinessId());
    }

    // Backs the Inventory search box + category filter, and the identical params on the
    // Sales/PO product pickers (both hit this same GET /products endpoint). Archived
    // products are excluded unless includeArchived=true, so a picker never lets someone
    // ring up a product that's been pulled from sale.
    public List<Product> search(String search, UUID categoryId, boolean includeArchived) {
        return productRepository.search(TenantContext.getBusinessId(), search, categoryId, includeArchived);
    }

    public List<Product> listLowStock() {
        return search(null, null, false).stream()
                .filter(p -> p.getQuantity() <= p.getLowStockThreshold())
                .toList();
    }

    public Product getOwned(UUID productId) {
        return productRepository.findByIdAndBusinessId(productId, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    @Transactional
    public Product create(ProductRequest req) {
        UUID businessId = TenantContext.getBusinessId();

        if (req.sku() != null && !req.sku().isBlank()
                && productRepository.existsByBusinessIdAndSku(businessId, req.sku())) {
            throw new ApiException(HttpStatus.CONFLICT, "A product with this SKU already exists.");
        }

        Product product = Product.builder()
                .businessId(businessId)
                .name(req.name())
                .category(req.category())
                .categoryId(req.categoryId())
                .sku(req.sku())
                .costPrice(req.costPrice() != null ? req.costPrice() : BigDecimal.ZERO)
                .sellingPrice(req.sellingPrice() != null ? req.sellingPrice() : BigDecimal.ZERO)
                .quantity(req.quantity() != null ? req.quantity() : 0)
                .lowStockThreshold(req.lowStockThreshold() != null ? req.lowStockThreshold() : 5)
                .supplierName(req.supplierName())
                .imageUrl(req.imageUrl())
                .build();

        product = productRepository.save(product);

        activityLogService.log("Added product \"" + product.getName() + "\"", "PRODUCT", product.getId());

        // Record the opening stock as the first movement, so stock history is complete from day one.
        if (product.getQuantity() > 0) {
            logMovement(product, MovementType.ADD, product.getQuantity(), product.getQuantity(), "Opening stock");
        }

        return product;
    }

    @Transactional
    public Product update(UUID productId, ProductRequest req) {
        Product product = getOwned(productId);

        if (req.sku() != null && !req.sku().isBlank() && !req.sku().equals(product.getSku())
                && productRepository.existsByBusinessIdAndSku(product.getBusinessId(), req.sku())) {
            throw new ApiException(HttpStatus.CONFLICT, "A product with this SKU already exists.");
        }

        product.setName(req.name());
        product.setCategory(req.category());
        product.setCategoryId(req.categoryId());
        product.setSku(req.sku());
        if (req.costPrice() != null) product.setCostPrice(req.costPrice());
        if (req.sellingPrice() != null) product.setSellingPrice(req.sellingPrice());
        if (req.lowStockThreshold() != null) product.setLowStockThreshold(req.lowStockThreshold());
        product.setSupplierName(req.supplierName());
        product.setImageUrl(req.imageUrl());
        // Quantity is deliberately NOT editable here — it only ever changes through
        // adjustStock(), so every change is logged in stock_movements.

        return productRepository.save(product);
    }

    // Archive, never hard delete — sale_items/purchase_order_items already snapshot
    // product_name, so this is safe as a single reversible path instead of a real delete.
    @Transactional
    public Product archive(UUID productId) {
        Product product = getOwned(productId);
        product.setActive(false);
        product = productRepository.save(product);
        activityLogService.log("Archived product \"" + product.getName() + "\"", "PRODUCT", product.getId());
        return product;
    }

    @Transactional
    public Product restore(UUID productId) {
        Product product = getOwned(productId);
        product.setActive(true);
        product = productRepository.save(product);
        activityLogService.log("Restored product \"" + product.getName() + "\"", "PRODUCT", product.getId());
        return product;
    }

    @Transactional
    public Product adjustStock(UUID productId, StockAdjustmentRequest req) {
        Product product = getOwned(productId);

        int newQuantity;
        int signedChange;

        switch (req.movementType()) {
            case ADD -> {
                newQuantity = product.getQuantity() + req.quantity();
                signedChange = req.quantity();
            }
            case REMOVE -> {
                newQuantity = product.getQuantity() - req.quantity();
                if (newQuantity < 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Can't remove more stock than is currently available (" + product.getQuantity() + " left).");
                }
                signedChange = -req.quantity();
            }
            case ADJUST -> {
                // ADJUST sets the absolute quantity (e.g. after a physical stock count),
                // rather than adding/subtracting a delta.
                newQuantity = req.quantity();
                signedChange = newQuantity - product.getQuantity();
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown movement type.");
        }

        product.setQuantity(newQuantity);
        productRepository.save(product);

        logMovement(product, req.movementType(), signedChange, newQuantity, req.note());
        activityLogService.log(
                "Stock " + pastTense(req.movementType()) + " for \"" + product.getName() + "\": "
                        + (signedChange >= 0 ? "+" : "") + signedChange + " (now " + newQuantity + ")",
                "PRODUCT", product.getId()
        );

        return product;
    }

    private String pastTense(MovementType type) {
        return switch (type) {
            case ADD -> "added";
            case REMOVE -> "removed";
            case ADJUST -> "adjusted";
        };
    }

    public List<StockMovement> stockHistory(UUID productId) {
        // Ensures the product belongs to this tenant before returning any history for it.
        Product product = getOwned(productId);
        return stockMovementRepository.findAllByProductIdAndBusinessIdOrderByCreatedAtDesc(
                product.getId(), product.getBusinessId());
    }

    private void logMovement(Product product, MovementType type, int change, int resultingQuantity, String note) {
        StockMovement movement = StockMovement.builder()
                .businessId(product.getBusinessId())
                .productId(product.getId())
                .movementType(type)
                .quantityChange(change)
                .resultingQuantity(resultingQuantity)
                .note(note)
                .createdBy(TenantContext.getUserId())
                .build();
        stockMovementRepository.save(movement);
    }
}
