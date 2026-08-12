package com.ratel.rbms.controller;

import com.ratel.rbms.dto.PurchaseOrderRequest;
import com.ratel.rbms.dto.PurchaseOrderResponse;
import com.ratel.rbms.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchase-orders")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping
    public List<PurchaseOrderResponse> list() {
        return purchaseOrderService.listAll();
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse get(@PathVariable UUID id) {
        return purchaseOrderService.get(id);
    }

    @PostMapping
    public ResponseEntity<PurchaseOrderResponse> create(@Valid @RequestBody PurchaseOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseOrderService.create(request));
    }

    @PostMapping("/{id}/receive")
    public PurchaseOrderResponse receive(@PathVariable UUID id) {
        return purchaseOrderService.receive(id);
    }

    @PostMapping("/{id}/cancel")
    public PurchaseOrderResponse cancel(@PathVariable UUID id) {
        return purchaseOrderService.cancel(id);
    }

    @PostMapping("/{id}/mark-paid")
    public PurchaseOrderResponse markPaid(@PathVariable UUID id) {
        return purchaseOrderService.markPaid(id);
    }
}
