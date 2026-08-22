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

    // An uploaded signature image (e.g. a scanned/photographed handwritten
    // signature), shown on generated invoices — same "upload once, reused
    // everywhere" idea as logoUrl. Optional: invoices render fine without one.
    @Column(name = "signature_url", length = 500)
    private String signatureUrl;

    // GRA TIN/VAT registration number or equivalent — shown on generated
    // invoices next to the business's own contact details. Free text (not a
    // format-validated field) since requirements vary by country/business type.
    @Column(name = "tax_id", length = 50)
    private String taxId;

    // Reusable default (warranty/payment terms etc.) — snapshotted onto each
    // new Invoice.termsAndConditions at creation time rather than joined
    // live, so editing this later never rewrites an already-issued invoice.
    @Column(name = "default_terms_and_conditions", columnDefinition = "TEXT")
    private String defaultTermsAndConditions;

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

    // Null = use the plan's list price as the monthly rate. Set = use this as
    // the negotiated monthly rate instead — the multi-month discount tiers in
    // BillingService.discountForMonths() still apply on top of whichever rate
    // is in effect, so this isn't a flat total, just the per-month base.
    @Column(name = "price_override", precision = 12, scale = 2)
    private BigDecimal priceOverride;

    // How many months the business most recently checked out for — set on
    // every successful BillingService.verifyPayment() (manual or auto-charge)
    // to payment.getMonths(). attemptAutoCharge() reads this back so a
    // saved-card renewal repeats the same cycle length/discount the business
    // originally chose. Defaults to 1 (pre-existing single-cycle behavior).
    @Column(name = "billing_cycle_months", nullable = false)
    @Builder.Default
    private int billingCycleMonths = 1;

    // Last time the "renew soon" reminder email went out, so the scheduled job
    // doesn't re-send it every day during the 3-day warning window.
    @Column(name = "expiry_reminder_sent_at")
    private Instant expiryReminderSentAt;

    // Simple text array of enabled module codes. Was inert scaffolding for a
    // long time (nothing enforced it) — now that ModuleAccessService actually
    // gates on it, a new business must default to EVERYTHING on, not just the
    // original narrow core set: this is meant as Super-Admin-initiated
    // *hiding* of an optional module for a specific business, never an
    // opt-in a new signup would silently start locked out of. See
    // V44__backfill_enabled_modules.sql for why every pre-existing row also
    // needed a one-time backfill to this same full set.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "enabled_modules", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private List<String> enabledModules = List.of(
            "INVENTORY", "SALES", "CUSTOMERS", "EXPENSES",
            "SERVICE_ORDERS", "CUSTOM_WIG_REQUESTS", "ECOMMERCE", "BOOKINGS", "SUPPLIERS_AND_PURCHASING"
    );

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
