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

    private String logoUrl;

    private String location;

    private String contactEmail;

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

    // Last time the "renew soon" reminder email went out, so the scheduled job
    // doesn't re-send it every day during the 3-day warning window.
    @Column(name = "expiry_reminder_sent_at")
    private Instant expiryReminderSentAt;

    // Simple text array of enabled module codes, e.g. INVENTORY, SALES, CUSTOMERS, EXPENSES
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "enabled_modules", columnDefinition = "text[]")
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
