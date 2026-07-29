package com.ratel.rbms.entity;

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

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
