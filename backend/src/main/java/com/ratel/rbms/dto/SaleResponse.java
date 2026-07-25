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
        Instant createdAt
) {
    public static SaleResponse from(Sale sale, String customerName, String cashierName, List<SaleItemResponse> items) {
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
                sale.getCreatedAt()
        );
    }
}
