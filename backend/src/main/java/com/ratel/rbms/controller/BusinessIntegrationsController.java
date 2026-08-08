package com.ratel.rbms.controller;

import com.ratel.rbms.dto.BlackoutDateRequest;
import com.ratel.rbms.dto.BlackoutDateResponse;
import com.ratel.rbms.dto.BusinessIntegrationsRequest;
import com.ratel.rbms.dto.BusinessIntegrationsResponse;
import com.ratel.rbms.dto.TestConnectionResponse;
import com.ratel.rbms.service.BusinessIntegrationsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    @GetMapping("/blackout-dates")
    public List<BlackoutDateResponse> listBlackoutDates() {
        return businessIntegrationsService.listBlackoutDates();
    }

    @PostMapping("/blackout-dates")
    public ResponseEntity<BlackoutDateResponse> addBlackoutDate(@Valid @RequestBody BlackoutDateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(businessIntegrationsService.addBlackoutDate(request));
    }

    @DeleteMapping("/blackout-dates/{id}")
    public ResponseEntity<Void> removeBlackoutDate(@PathVariable UUID id) {
        businessIntegrationsService.removeBlackoutDate(id);
        return ResponseEntity.noContent().build();
    }
}
