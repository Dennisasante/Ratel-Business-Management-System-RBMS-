package com.ratel.rbms.controller;

import com.ratel.rbms.dto.BusinessIntegrationsRequest;
import com.ratel.rbms.dto.BusinessIntegrationsResponse;
import com.ratel.rbms.dto.PaymentGatewayStatusResponse;
import com.ratel.rbms.dto.TestConnectionResponse;
import com.ratel.rbms.service.BusinessIntegrationsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations")
@PreAuthorize("hasRole('OWNER')")
public class BusinessIntegrationsController {

    private final BusinessIntegrationsService businessIntegrationsService;

    public BusinessIntegrationsController(BusinessIntegrationsService businessIntegrationsService) {
        this.businessIntegrationsService = businessIntegrationsService;
    }

    @GetMapping
    public BusinessIntegrationsResponse get() {
        return businessIntegrationsService.get();
    }

    // Overrides the class-level OWNER-only gate — every receipt page (service
    // order, sale) needs to know "can this be charged through Paystack," and
    // those are viewed/printed by Manager/Sales Person/Accountant too, not
    // just Owner. Returns only a boolean, never the actual keys.
    @GetMapping("/payment-status")
    @PreAuthorize("isAuthenticated()")
    public PaymentGatewayStatusResponse paymentStatus() {
        return businessIntegrationsService.getPaymentGatewayStatus();
    }

    @PutMapping
    public BusinessIntegrationsResponse update(@RequestBody BusinessIntegrationsRequest request) {
        return businessIntegrationsService.update(request);
    }

    @PostMapping("/test-paystack")
    public TestConnectionResponse testPaystack() {
        return businessIntegrationsService.testPaystackConnection();
    }

    @PostMapping("/test-woocommerce")
    public TestConnectionResponse testWooCommerce() {
        return businessIntegrationsService.testWooCommerceConnection();
    }
}
