package com.ratel.rbms.dto;

import com.ratel.rbms.entity.Invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceSummaryResponse(
        UUID id,
        Long invoiceNumber,
        String customerName,
        LocalDate issueDate,
        LocalDate dueDate,
        String status,
        BigDecimal totalAmount
) {
    public static InvoiceSummaryResponse from(Invoice invoice) {
        return new InvoiceSummaryResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getCustomerName(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getStatus(),
                invoice.getTotalAmount()
        );
    }
}
