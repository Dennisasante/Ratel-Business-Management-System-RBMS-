package com.ratel.rbms.controller;

import com.ratel.rbms.dto.ProductRequest;
import com.ratel.rbms.dto.ProductResponse;
import com.ratel.rbms.dto.StockAdjustmentRequest;
import com.ratel.rbms.dto.StockMovementResponse;
import com.ratel.rbms.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // Backs the Inventory list, and — via the same params — the Sales and PO product
    // picker modals, so all three stay in sync instead of drifting apart.
    @GetMapping
    public List<ProductResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived
    ) {
        return productService.search(search, categoryId, includeArchived).stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/low-stock")
    public List<ProductResponse> lowStock() {
        return productService.listLowStock().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return ProductResponse.from(productService.getOwned(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = ProductResponse.from(productService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.update(id, request));
    }

    @PatchMapping("/{id}/archive")
    public ProductResponse archive(@PathVariable UUID id) {
        return ProductResponse.from(productService.archive(id));
    }

    @PatchMapping("/{id}/restore")
    public ProductResponse restore(@PathVariable UUID id) {
        return ProductResponse.from(productService.restore(id));
    }

    @PostMapping("/{id}/stock")
    public ProductResponse adjustStock(@PathVariable UUID id, @Valid @RequestBody StockAdjustmentRequest request) {
        return ProductResponse.from(productService.adjustStock(id, request));
    }

    @GetMapping("/{id}/stock-history")
    public List<StockMovementResponse> stockHistory(@PathVariable UUID id) {
        return productService.stockHistory(id).stream().map(StockMovementResponse::from).toList();
    }
}
