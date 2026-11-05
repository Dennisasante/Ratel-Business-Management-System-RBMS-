package com.ratel.rbms.controller;

import com.ratel.rbms.dto.CheckoutResponse;
import com.ratel.rbms.dto.SmsAccountResponse;
import com.ratel.rbms.dto.SmsBundleResponse;
import com.ratel.rbms.dto.SmsEstimateRequest;
import com.ratel.rbms.dto.SmsLedgerResponse;
import com.ratel.rbms.dto.SmsMessageResponse;
import com.ratel.rbms.dto.SmsPurchaseCheckoutRequest;
import com.ratel.rbms.dto.SmsReceiptModeRequest;
import com.ratel.rbms.dto.SmsReceiptPreviewResponse;
import com.ratel.rbms.dto.SmsReceiptStatusResponse;
import com.ratel.rbms.dto.SmsPurchaseVerifyResponse;
import com.ratel.rbms.dto.SmsSendTestRequest;
import com.ratel.rbms.dto.VerifyPaymentRequest;
import com.ratel.rbms.service.SmsCreditService;
import com.ratel.rbms.service.SmsReceiptService;
import com.ratel.rbms.service.SmsService;
import com.ratel.rbms.sms.SmsSegmentCalculator;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Texting customers spends real money, so it's an Owner/Manager tool; buying
// credits is a payment, so that part is Owner-only (same line as billing).
@RestController
@RequestMapping("/api/sms")
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
public class SmsController {

    private final SmsService smsService;
    private final SmsCreditService smsCreditService;
    private final SmsReceiptService smsReceiptService;

    public SmsController(SmsService smsService, SmsCreditService smsCreditService, SmsReceiptService smsReceiptService) {
        this.smsService = smsService;
        this.smsCreditService = smsCreditService;
        this.smsReceiptService = smsReceiptService;
    }

    @GetMapping("/account")
    public SmsAccountResponse account() {
        return smsService.getAccount();
    }

    @GetMapping("/messages")
    public List<SmsMessageResponse> messages() {
        return smsService.history();
    }

    @GetMapping("/ledger")
    public List<SmsLedgerResponse> ledger() {
        return smsService.creditHistory();
    }

    @PostMapping("/estimate")
    public SmsSegmentCalculator.Result estimate(@Valid @RequestBody SmsEstimateRequest request) {
        return smsService.estimate(request.message());
    }

    @PostMapping("/test-send")
    public SmsMessageResponse testSend(@Valid @RequestBody SmsSendTestRequest request) {
        return smsService.sendTest(request);
    }

    @PatchMapping("/receipt-mode")
    public SmsAccountResponse receiptMode(@Valid @RequestBody SmsReceiptModeRequest request) {
        return smsService.setReceiptMode(request.mode());
    }

    @GetMapping("/receipt-preview")
    public SmsReceiptPreviewResponse receiptPreview() {
        return smsReceiptService.preview();
    }

    // The cashier's screen after "complete order" - so cashiers (not just managers) can use these two.
    @GetMapping("/receipts/{saleId}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON')")
    public SmsReceiptStatusResponse receiptStatus(@PathVariable UUID saleId) {
        return smsReceiptService.statusFor(saleId);
    }

    @PostMapping("/receipts/{saleId}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON')")
    public SmsReceiptStatusResponse sendReceipt(@PathVariable UUID saleId) {
        return smsReceiptService.sendManually(saleId);
    }

    @GetMapping("/bundles")
    @PreAuthorize("hasRole('OWNER')")
    public List<SmsBundleResponse> bundles() {
        return smsCreditService.listBundles();
    }

    @GetMapping("/pricing")
    @PreAuthorize("hasRole('OWNER')")
    public com.ratel.rbms.dto.SmsPricingResponse pricing() {
        return smsCreditService.pricing();
    }

    @PostMapping("/purchase/amount")
    @PreAuthorize("hasRole('OWNER')")
    public CheckoutResponse checkoutByAmount(@Valid @RequestBody com.ratel.rbms.dto.SmsAmountCheckoutRequest request) {
        return smsCreditService.startPurchaseByAmount(request.amount());
    }

    @PostMapping("/purchase/checkout")
    @PreAuthorize("hasRole('OWNER')")
    public CheckoutResponse checkout(@Valid @RequestBody SmsPurchaseCheckoutRequest request) {
        return smsCreditService.startPurchase(request.bundleId());
    }

    @PostMapping("/purchase/verify")
    @PreAuthorize("hasRole('OWNER')")
    public SmsPurchaseVerifyResponse verify(@Valid @RequestBody VerifyPaymentRequest request) {
        return smsCreditService.verifyOwnPurchase(request.reference());
    }
}
