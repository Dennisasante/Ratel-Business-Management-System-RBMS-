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

@Entity
@Table(name = "subscription_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPayment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "subscription_plan_id", nullable = false)
    private UUID subscriptionPlanId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    // Idempotency key — a webhook and a client-triggered verify for the same
    // payment resolve to the same row instead of double-extending the period.
    @Column(name = "paystack_reference", nullable = false, unique = true, length = 100)
    private String paystackReference;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING, SUCCESS, FAILED

    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end")
    private Instant periodEnd;

    @Column(name = "paid_at")
    private Instant paidAt;

    // Captured from a successful verify response when Paystack returns a
    // reusable authorization — kept per-payment (not just on Business) so
    // there's a record of which specific transaction the saved card came from.
    @Column(name = "authorization_code", length = 100)
    private String authorizationCode;

    // Set at checkout time from the Owner's "save this card" checkbox — read
    // back in BillingService.verifyPayment() once the payment succeeds, since
    // checkout and verify are separate requests.
    @Column(name = "save_card_requested", nullable = false)
    @Builder.Default
    private boolean saveCardRequested = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
