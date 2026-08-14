package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// One row per payment event across the business, whatever flow it came from
// (service order, sale, booking, purchase order) and whether it went through a
// gateway at all (gateway=MANUAL for a plain cash/manual record). Kept separate
// from SubscriptionPayment — that's Ratel's own platform revenue, not a
// business's own customer/supplier payments.
@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType sourceType;

    // Nullable — not every transaction has to trace back to a specific row
    // (kept open for future manual/adjustment entries with no source record).
    @Column(name = "source_id")
    private UUID sourceId;

    @Column(nullable = false, length = 20)
    private String gateway;

    @Column(length = 20)
    private String method;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "GHS";

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "gateway_reference", length = 100)
    private String gatewayReference;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum Direction { INCOMING, OUTGOING }

    public enum SourceType { SERVICE_ORDER, SALE, BOOKING, PURCHASE_ORDER, CUSTOM_WIG_REQUEST }
}
