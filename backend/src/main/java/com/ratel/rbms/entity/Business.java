package com.ratel.rbms.entity;

import com.ratel.rbms.entity.enums.BillingStatus;
import com.ratel.rbms.entity.enums.Industry;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Business {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    // URL-friendly identifier for the hosted booking page (ratel.app/book/{slug}),
    // for businesses with no website of their own to embed the widget on.
    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Industry industry;

    @Column(length = 500)
    private String logoUrl;

    private String location;

    @Column(length = 150)
    private String contactEmail;

    @Column(length = 50)
    private String contactPhone;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "GHS";

    // Superseded by subscriptionPlanId/billingStatus below — kept for one release
    // as a safety net, then dropped.
    @Column(name = "subscription_plan", nullable = false, length = 30)
    @Builder.Default
    private String subscriptionPlan = "FREE_TRIAL";

    @Column(name = "subscription_plan_id")
    private UUID subscriptionPlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_status", nullable = false, length = 20)
    @Builder.Default
    private BillingStatus billingStatus = BillingStatus.TRIALING;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "current_period_ends_at")
    private Instant currentPeriodEndsAt;

    // Only set while billingStatus == GRACE — the deadline after which
    // BillingExpiryService flips a lapsed-but-in-grace business to READ_ONLY.
    @Column(name = "grace_period_ends_at")
    private Instant gracePeriodEndsAt;

    // Captured from a successful checkout's verify response when Paystack
    // returns a reusable authorization — set independently of autoRenewEnabled,
    // which is a separate explicit opt-in (paying by card doesn't imply
    // wanting auto-renewal).
    @Column(name = "paystack_authorization_code", length = 100)
    private String paystackAuthorizationCode;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "card_brand", length = 30)
    private String cardBrand;

    @Column(name = "auto_renew_enabled", nullable = false)
    @Builder.Default
    private boolean autoRenewEnabled = false;

    // Null = charge the plan's list price. Set = charge this business exactly
    // this amount instead — a negotiated rate, not a separate plan.
    @Column(name = "price_override", precision = 12, scale = 2)
    private BigDecimal priceOverride;

    // Last time the "renew soon" reminder email went out, so the scheduled job
    // doesn't re-send it every day during the 3-day warning window.
    @Column(name = "expiry_reminder_sent_at")
    private Instant expiryReminderSentAt;

    // Simple text array of enabled module codes, e.g. INVENTORY, SALES, CUSTOMERS, EXPENSES
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "enabled_modules", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private List<String> enabledModules = List.of("INVENTORY", "SALES", "CUSTOMERS", "EXPENSES");

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
