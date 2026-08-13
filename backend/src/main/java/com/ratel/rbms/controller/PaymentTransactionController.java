package com.ratel.rbms.controller;

import com.ratel.rbms.dto.PaymentTransactionResponse;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.service.BookingService;
import com.ratel.rbms.service.PaymentTransactionService;
import com.ratel.rbms.service.SaleService;
import com.ratel.rbms.service.ServiceOrderService;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// Business-wide money-movement visibility — same role gate as the other
// financial reports (ServiceOrderController.report, ReportController), not
// scoped to STAFF's own work.
@RestController
@RequestMapping("/api/payment-transactions")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class PaymentTransactionController {

    private final PaymentTransactionService paymentTransactionService;
    private final SaleService saleService;
    private final ServiceOrderService serviceOrderService;
    private final BookingService bookingService;

    public PaymentTransactionController(
            PaymentTransactionService paymentTransactionService,
            SaleService saleService,
            ServiceOrderService serviceOrderService,
            BookingService bookingService
    ) {
        this.paymentTransactionService = paymentTransactionService;
        this.saleService = saleService;
        this.serviceOrderService = serviceOrderService;
        this.bookingService = bookingService;
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

    // Cross-checks a stuck/PENDING gateway transaction against Paystack directly —
    // covers the case where a webhook/notification never arrived on either end but
    // the charge actually went through. Delegates to each flow's own verifyPayment(),
    // the same never-trust-the-client path a customer-triggered verify already uses,
    // so this can't desync the Sale/ServiceOrder/Booking's own paymentStatus from the ledger.
    @PostMapping("/{id}/verify")
    public ResponseEntity<Void> verify(@PathVariable UUID id) {
        PaymentTransaction t = paymentTransactionService.getOwned(TenantContext.getBusinessId(), id);

        if (!"PAYSTACK".equals(t.getGateway()) || t.getGatewayReference() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing to verify — this wasn't a gateway payment.");
        }

        switch (t.getSourceType()) {
            case SALE -> saleService.verifyPayment(t.getGatewayReference());
            case SERVICE_ORDER -> serviceOrderService.verifyPayment(t.getGatewayReference());
            case BOOKING -> bookingService.verifyPayment(t.getGatewayReference());
            case PURCHASE_ORDER -> throw new ApiException(HttpStatus.BAD_REQUEST, "Purchase orders are paid manually — there's nothing to verify.");
        }

        return ResponseEntity.noContent().build();
    }
}
