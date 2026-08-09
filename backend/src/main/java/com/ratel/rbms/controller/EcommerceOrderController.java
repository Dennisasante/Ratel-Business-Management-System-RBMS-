package com.ratel.rbms.controller;

import com.ratel.rbms.dto.EcommerceOrderDetailResponse;
import com.ratel.rbms.dto.EcommerceOrderResponse;
import com.ratel.rbms.dto.UpdateEcommerceOrderStatusRequest;
import com.ratel.rbms.service.EcommerceOrderService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ecommerce-orders")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class EcommerceOrderController {

    private final EcommerceOrderService ecommerceOrderService;

    public EcommerceOrderController(EcommerceOrderService ecommerceOrderService) {
        this.ecommerceOrderService = ecommerceOrderService;
    }

    @GetMapping
    public List<EcommerceOrderResponse> list() {
        return ecommerceOrderService.list();
    }

    @GetMapping("/{id}")
    public EcommerceOrderDetailResponse get(@PathVariable UUID id) {
        return ecommerceOrderService.get(id);
    }

    @PatchMapping("/{id}/status")
    public EcommerceOrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateEcommerceOrderStatusRequest request) {
        return ecommerceOrderService.updateStatus(id, request.status());
    }
}
