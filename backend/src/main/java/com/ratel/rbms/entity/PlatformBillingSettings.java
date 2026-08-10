package com.ratel.rbms.entity;

import com.ratel.rbms.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Single-row global setting, managed by the super admin — see PlatformBillingSettingsRepository
 * for how "the one row" is fetched.
 */
@Entity
@Table(name = "platform_billing_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformBillingSettings {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "trial_days", nullable = false)
    @Builder.Default
    private int trialDays = 14;

    // GHS-per-USD, e.g. 15.20 — manually set by the super admin, no external FX API.
    // Null hides the USD display toggle entirely on the Billing page.
    @Column(name = "usd_display_rate", precision = 10, scale = 4)
    private BigDecimal usdDisplayRate;

    // RBMS's OWN Paystack keys — subscription billing charged to businesses,
    // completely separate from each business's own Paystack keys (see
    // BusinessIntegrations). Falls back to the PAYSTACK_SECRET_KEY/
    // PAYSTACK_PUBLIC_KEY env vars when blank, see PaystackService/BillingService.
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "paystack_secret_key")
    private String paystackSecretKey;

    @Column(name = "paystack_public_key")
    private String paystackPublicKey;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
