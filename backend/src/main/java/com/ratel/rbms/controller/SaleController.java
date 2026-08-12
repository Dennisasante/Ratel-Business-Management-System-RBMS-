package com.ratel.rbms.controller;

import com.ratel.rbms.dto.CheckoutResponse;
import com.ratel.rbms.dto.MobileMoneyChargeRequest;
import com.ratel.rbms.dto.MobileMoneyChargeResponse;
import com.ratel.rbms.dto.SaleRequest;
import com.ratel.rbms.dto.SaleResponse;
import com.ratel.rbms.dto.VerifyPaymentRequest;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.service.ReceiptService;
import com.ratel.rbms.service.SaleService;
import com.ratel.rbms.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sales")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class SaleController {

    private final SaleService saleService;
    private final ReceiptService receiptService;
    private final BusinessRepository businessRepository;

    public SaleController(SaleService saleService, ReceiptService receiptService, BusinessRepository businessRepository) {
        this.saleService = saleService;
        this.receiptService = receiptService;
        this.businessRepository = businessRepository;
    }

    @GetMapping
    public List<SaleResponse> list() {
        return saleService.listAll();
    }

    @GetMapping("/{id}")
    public SaleResponse get(@PathVariable UUID id) {
        return saleService.get(id);
    }

    @PostMapping
    public ResponseEntity<SaleResponse> create(@Valid @RequestBody SaleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(saleService.createSale(request));
    }

    @PostMapping("/{id}/checkout")
    public CheckoutResponse startPayment(@PathVariable UUID id) {
        return saleService.startPayment(id);
    }

    @PostMapping("/{id}/charge-mobile-money")
    public MobileMoneyChargeResponse chargeMobileMoney(@PathVariable UUID id, @Valid @RequestBody MobileMoneyChargeRequest request) {
        return saleService.chargeMobileMoney(id, request);
    }

    @PostMapping("/verify")
    public SaleResponse verifyPayment(@Valid @RequestBody VerifyPaymentRequest request) {
        return saleService.verifyPayment(request.reference());
    }

    @PostMapping("/{id}/mark-paid")
    public SaleResponse markPaid(@PathVariable UUID id) {
        return saleService.markPaid(id);
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID id) {
        SaleResponse sale = saleService.get(id);
        Business business = businessRepository.findById(TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));

        byte[] pdf = receiptService.generateReceipt(business, sale);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=receipt-" + sale.saleNumber() + ".pdf")
                .body(pdf);
    }
}
