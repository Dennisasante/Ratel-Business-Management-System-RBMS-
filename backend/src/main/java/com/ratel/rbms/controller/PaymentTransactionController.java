package com.ratel.rbms.controller;

import com.ratel.rbms.dto.PaymentTransactionResponse;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.service.PaymentTransactionService;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

// Business-wide money-movement visibility — same role gate as the other
// financial reports (ServiceOrderController.report, ReportController), not
// scoped to STAFF's own work.
@RestController
@RequestMapping("/api/payment-transactions")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class PaymentTransactionController {

    private final PaymentTransactionService paymentTransactionService;

    public PaymentTransactionController(PaymentTransactionService paymentTransactionService) {
        this.paymentTransactionService = paymentTransactionService;
    }

    @GetMapping
    public List<PaymentTransactionResponse> list(
            @RequestParam(required = false) PaymentTransaction.Direction direction,
            @RequestParam(required = false) String gateway,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        return paymentTransactionService.search(TenantContext.getBusinessId(), direction, gateway, fromInstant, toInstant);
    }
}
