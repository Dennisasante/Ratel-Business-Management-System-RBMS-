package com.ratel.rbms.entity;

import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceOrder {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    // DB-assigned via BIGSERIAL, read back after insert — same pattern as PurchaseOrder.poNumber.
    @Generated(GenerationTime.INSERT)
    @Column(name = "order_number", insertable = false, updatable = false)
    private Long orderNumber;

    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ServiceOrderStatus status = ServiceOrderStatus.RECEIVED;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "service_catalog_id")
    private UUID serviceCatalogId;

    // A booking-originated order references exactly one of serviceCatalogId
    // (a plain service) or servicePackageId (a bundle) — never both.
    @Column(name = "service_package_id")
    private UUID servicePackageId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // The final charged amount — unchanged meaning, still what every report sums as revenue.
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    // Purely a transparency/reporting field: how much was knocked off at the attendant's/
    // owner's discretion to arrive at `price`. Not used in any revenue calculation itself.
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "assigned_staff_id")
    private UUID assignedStaffId;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    // Nullable — set only when a client books ahead for a future slot; most orders
    // are still same-day walk-ins.
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "ready_email_sent_at")
    private Instant readyEmailSentAt;

    @Column(name = "created_by")
    private UUID createdBy;

    // UNPAID/PAID/FAILED — a staff-created walk-in order has no Booking row to
    // carry payment status on, unlike the public booking-widget flow.
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private String paymentStatus = "UNPAID";

    @Column(name = "paystack_reference", unique = true, length = 100)
    private String paystackReference;

    // How much of price has actually been collected — drives the
    // UNPAID/PARTIALLY_PAID/PAID derivation in ServiceOrderService.recordPayment().
    // balanceDue (price - amountPaid) is never persisted, only derived.
    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
