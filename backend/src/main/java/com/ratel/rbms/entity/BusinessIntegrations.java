package com.ratel.rbms.entity;

import com.ratel.rbms.security.EncryptedStringConverter;
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
import java.time.LocalTime;
import java.util.List;
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

    // ISO weekday numbers (1=Mon..7=Sun) the business takes online bookings on.
    // Business-wide for now — see BookingService for the enforcement logic.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "working_days", columnDefinition = "int[]")
    @Builder.Default
    private List<Integer> workingDays = List.of(1, 2, 3, 4, 5, 6);

    @Column(name = "working_hours_start", nullable = false)
    @Builder.Default
    private LocalTime workingHoursStart = LocalTime.of(9, 0);

    @Column(name = "working_hours_end", nullable = false)
    @Builder.Default
    private LocalTime workingHoursEnd = LocalTime.of(18, 0);

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
