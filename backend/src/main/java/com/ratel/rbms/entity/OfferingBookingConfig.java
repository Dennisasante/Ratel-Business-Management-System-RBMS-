package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5A — the scheduling/booking-specific configuration for one
 * {@link Offering}, split out from {@code Offering.active} deliberately: {@code active} is
 * identity-level (meaningful to a plain Sale too, which never cares about scheduling), everything
 * here is meaningful only to the booking use case — mirrors this codebase's own existing
 * {@code Business}/{@code BusinessWorkingHours} 1:1-optional-config precedent. Present only for
 * Offerings actually offered as bookable (SERVICE, PACKAGE today).
 *
 * <p>{@code requiresLocation} is a real column here for PACKAGE offerings too — closing the
 * legacy {@code ServicePackage} asymmetry ({@code BookingService} hardcodes {@code false} for
 * packages today, since {@code ServicePackage} itself has no such column). The Phase 5A backfill
 * still populates it as {@code false} for backfilled packages (preserving current behaviour
 * exactly) — a business can edit it afterward once this becomes load-bearing in a later phase.
 */
@Entity
@Table(name = "offering_booking_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferingBookingConfig {

    @Id
    @Column(name = "offering_id")
    private UUID offeringId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "bookable_online", nullable = false)
    @Builder.Default
    private boolean bookableOnline = false;

    @Column(name = "duration_minutes", nullable = false)
    @Builder.Default
    private int durationMinutes = 30;

    @Column(name = "max_concurrent_bookings", nullable = false)
    @Builder.Default
    private int maxConcurrentBookings = 1;

    @Column(name = "requires_location", nullable = false)
    @Builder.Default
    private boolean requiresLocation = false;

    // NONE/DEPOSIT/FULL, or null to fall back to the business's own default
    // (BusinessIntegrations.bookingPaymentPolicy) — identical fallback semantics to the legacy
    // paymentPolicyOverride columns this mirrors.
    @Column(name = "payment_policy_override", length = 20)
    private String paymentPolicyOverride;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
