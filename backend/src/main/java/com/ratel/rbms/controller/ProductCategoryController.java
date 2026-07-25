package com.ratel.rbms.controller;

import com.ratel.rbms.dto.ProductCategoryRequest;
import com.ratel.rbms.dto.ProductCategoryResponse;
import com.ratel.rbms.service.ProductCategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/product-categories")
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    public ProductCategoryController(ProductCategoryService productCategoryService) {
        this.productCategoryService = productCategoryService;
    }

    @GetMapping
    public List<ProductCategoryResponse> list() {
        return productCategoryService.listAll();
    }

    @PostMapping
    public ResponseEntity<ProductCategoryResponse> create(@Valid @RequestBody ProductCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productCategoryService.create(request));
    }

    @PutMapping("/{id}")
    public ProductCategoryResponse rename(@PathVariable UUID id, @Valid @RequestBody ProductCategoryRequest request) {
        return productCategoryService.rename(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productCategoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
