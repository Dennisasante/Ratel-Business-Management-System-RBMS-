package com.ratel.rbms.entity;

import com.ratel.rbms.security.EncryptedStringConverter;
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
import java.util.UUID;

/**
 * One row per business — the client's OWN Paystack/WooCommerce credentials,
 * completely separate from the platform's own Paystack keys used for RBMS
 * subscription billing (see BillingService). Their customers pay them
 * directly through these; nothing here ever touches Ratel's own revenue.
 */
@Entity
@Table(name = "business_integrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessIntegrations {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;

    @Column(name = "paystack_public_key")
    private String paystackPublicKey;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "paystack_secret_key")
    private String paystackSecretKey;

    @Column(name = "woocommerce_site_url")
    private String woocommerceSiteUrl;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "woocommerce_consumer_key")
    private String woocommerceConsumerKey;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "woocommerce_consumer_secret")
    private String woocommerceConsumerSecret;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "woocommerce_webhook_secret")
    private String woocommerceWebhookSecret;

    @Column(name = "whatsapp_notify_number", length = 20)
    private String whatsappNotifyNumber;

    // NONE, DEPOSIT, or FULL — whether a booking counts as confirmed only
    // once the customer has paid (in full or a deposit) or as soon as it's
    // submitted. See BookingService for the enforcement logic.
    @Column(name = "booking_payment_policy", nullable = false, length = 20)
    @Builder.Default
    private String bookingPaymentPolicy = "NONE";

    @Column(name = "booking_deposit_percent", nullable = false)
    @Builder.Default
    private Integer bookingDepositPercent = 50;

    // When the policy above requires payment, whether a customer can still
    // opt to pay in person instead of through Paystack — off by default so
    // a DEPOSIT/FULL policy stays a hard requirement unless the owner
    // explicitly opts in.
    @Column(name = "booking_allow_pay_in_person", nullable = false)
    @Builder.Default
    private boolean allowPayInPerson = false;

    // Hours before the scheduled appointment that cancel/reschedule stop
    // being allowed. 0 = no restriction (today's behavior).
    @Column(name = "booking_cancellation_cutoff_hours", nullable = false)
    @Builder.Default
    private int cancellationCutoffHours = 0;

    @Column(name = "test_mode", nullable = false)
    @Builder.Default
    private boolean testMode = false;

    // In-app "New sale" notification (the bell, Owner+Manager) — on by default,
    // same as every other notification type having no opt-out; Owner-only to
    // change, since it's a business-wide setting like the rest of this entity.
    @Column(name = "notify_on_sale", nullable = false)
    @Builder.Default
    private boolean notifyOnSale = true;

    // Thermal receipt printer (e.g. Xprinter XP-80T) — off by default, since
    // most businesses don't have one. Printing itself always goes through
    // the browser's native print dialog against whatever's installed as a
    // system printer (see ReceiptView.tsx) — this only remembers whether to
    // show/auto-trigger that and which paper width to default to, never
    // talks to a printer directly, so it's equally usable for a paired
    // Bluetooth printer once the OS recognizes it as a normal printer.
    @Column(name = "receipt_printer_enabled", nullable = false)
    @Builder.Default
    private boolean receiptPrinterEnabled = false;

    // "58" or "80" (mm) — validated in BusinessIntegrationsService.update().
    @Column(name = "receipt_printer_paper_width", nullable = false, length = 5)
    @Builder.Default
    private String receiptPrinterPaperWidth = "80";

    // Below this gross margin %, a product surfaces under "Low Margin
    // Products" on the dashboard (see DashboardService) — configurable per
    // business rather than a single hardcoded threshold for everyone.
    @Column(name = "min_profit_margin_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal minProfitMarginPercent = new BigDecimal("15.00");

    // PAYSTACK today — only value supported. Kept as an explicit field (rather
    // than inferring "which gateway" purely from which secret key is set) so a
    // future second gateway (e.g. Hubtel) has a real selector to switch on
    // instead of a guess.
    @Column(name = "payment_gateway", nullable = false, length = 20)
    @Builder.Default
    private String paymentGateway = "PAYSTACK";

    // Working hours moved to BusinessWorkingHours (one row per open day) so a
    // day like Sunday can carry its own hours — see that entity.

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
