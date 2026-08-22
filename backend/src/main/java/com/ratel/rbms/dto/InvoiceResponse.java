package com.ratel.rbms.dto;

import com.ratel.rbms.entity.Invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        Long invoiceNumber,
        UUID customerId,
        String customerName,
        String customerEmail,
        String customerPhone,
        String customerAddress,
        LocalDate issueDate,
        LocalDate dueDate,
        String notes,
        String termsAndConditions,
        String status,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal shippingAmount,
        BigDecimal totalAmount,
        List<InvoiceItemResponse> items,
        Instant createdAt
) {
    public static InvoiceResponse from(Invoice invoice, List<InvoiceItemResponse> items) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getCustomerId(),
                invoice.getCustomerName(),
                invoice.getCustomerEmail(),
                invoice.getCustomerPhone(),
                invoice.getCustomerAddress(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getNotes(),
                invoice.getTermsAndConditions(),
                invoice.getStatus(),
                invoice.getSubtotal(),
                invoice.getDiscountAmount(),
                invoice.getTaxRate(),
                invoice.getTaxAmount(),
                invoice.getShippingAmount(),
                invoice.getTotalAmount(),
                items,
                invoice.getCreatedAt()
        );
    }
}
