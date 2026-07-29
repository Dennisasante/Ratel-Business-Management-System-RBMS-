package com.ratel.rbms.controller;

import com.ratel.rbms.dto.BillingStatusResponse;
import com.ratel.rbms.dto.CheckoutRequest;
import com.ratel.rbms.dto.CheckoutResponse;
import com.ratel.rbms.dto.SubscriptionPaymentResponse;
import com.ratel.rbms.dto.SubscriptionPlanResponse;
import com.ratel.rbms.dto.VerifyPaymentRequest;
import com.ratel.rbms.dto.VerifyPaymentResponse;
import com.ratel.rbms.service.BillingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Billing shouldn't be a staff-level action — every endpoint here is Owner-only,
// including read-only ones, so a Manager/staff member never even sees plan
// pricing or payment history.
@RestController
@RequestMapping("/api/billing")
@PreAuthorize("hasRole('OWNER')")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/status")
    public BillingStatusResponse status() {
        return billingService.getStatus();
    }

    @GetMapping("/plans")
    public List<SubscriptionPlanResponse> plans() {
        return billingService.listPlans();
    }

    @GetMapping("/history")
    public List<SubscriptionPaymentResponse> history() {
        return billingService.history();
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return billingService.startCheckout(request.planId());
    }

    @PostMapping("/verify")
    public VerifyPaymentResponse verify(@Valid @RequestBody VerifyPaymentRequest request) {
        return billingService.verifyPayment(request.reference());
    }
}
