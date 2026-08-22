package com.ratel.rbms.controller;

import com.ratel.rbms.dto.ImportConfirmRequest;
import com.ratel.rbms.dto.ImportPreviewResponse;
import com.ratel.rbms.dto.ImportResultResponse;
import com.ratel.rbms.service.ProductImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products/import")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class ProductImportController {

    private final ProductImportService productImportService;

    public ProductImportController(ProductImportService productImportService) {
        this.productImportService = productImportService;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventory-import-template.csv")
                .body(productImportService.template());
    }

    @PostMapping("/preview")
    public ImportPreviewResponse preview(@RequestParam("file") MultipartFile file) {
        return productImportService.preview(file);
    }

    @PostMapping("/confirm")
    public ImportResultResponse confirm(@Valid @RequestBody ImportConfirmRequest request) {
        return productImportService.confirm(request.rows());
    }
}
