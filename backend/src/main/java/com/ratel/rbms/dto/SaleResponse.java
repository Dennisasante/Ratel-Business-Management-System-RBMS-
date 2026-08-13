package com.ratel.rbms.dto;

import com.ratel.rbms.entity.Sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        Long saleNumber,
        UUID customerId,
        String customerName,
        String cashierName,
        String paymentMethod,
        BigDecimal totalAmount,
        BigDecimal commissionAmount,
        List<SaleItemResponse> items,
        Instant createdAt,
        // UNPAID/PARTIALLY_PAID/PAID/FAILED/REFUNDED — CASH/MOBILE_MONEY_DIRECT
        // sales are PAID immediately (assumed collected in person); MOBILE_MONEY
        // (Online Payment) starts UNPAID until charged or manually marked paid.
        // See Sale.paymentStatus.
        String paymentStatus,
        BigDecimal amountPaid,
        // Derived, never persisted — totalAmount minus amountPaid, clamped to zero.
        BigDecimal balanceDue
) {
    public static SaleResponse from(Sale sale, String customerName, String cashierName, List<SaleItemResponse> items) {
        BigDecimal balanceDue = sale.getTotalAmount().subtract(sale.getAmountPaid()).max(BigDecimal.ZERO);
        return new SaleResponse(
                sale.getId(),
                sale.getSaleNumber(),
                sale.getCustomerId(),
                customerName,
                cashierName,
                sale.getPaymentMethod().name(),
                sale.getTotalAmount(),
                sale.getCommissionAmount(),
                items,
                sale.getCreatedAt(),
                sale.getPaymentStatus(),
                sale.getAmountPaid(),
                balanceDue
        );
    }
}
