package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    // Assigned in InvoiceService.create() under a row lock on the business
    // (BusinessRepository.findByIdForUpdate) — sequential PER BUSINESS,
    // unlike Sale.saleNumber/ServiceOrder.orderNumber which are a single
    // shared BIGSERIAL. A customer-facing invoice should read "Invoice #1"
    // on a business's very first one, not some large shared counter value.
    @Column(name = "invoice_number", nullable = false)
    private Long invoiceNumber;

    @Column(name = "customer_id")
    private UUID customerId;

    // Snapshotted, not just joined through customerId — covers a client who
    // isn't in the Customer list at all, and keeps a sent invoice's "Bill To"
    // block correct even if the customer is later renamed/deleted.
    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    @Column(name = "customer_phone", length = 50)
    private String customerPhone;

    @Column(name = "customer_address", length = 300)
    private String customerAddress;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // DRAFT/SENT/PAID/OVERDUE — a plain label with no downstream business
    // logic (unlike ServiceOrderStatus), so no transition graph is needed.
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
