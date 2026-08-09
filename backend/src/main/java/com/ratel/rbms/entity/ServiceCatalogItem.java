package com.ratel.rbms.entity;

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

@Entity
@Table(name = "service_catalog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceCatalogItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    // Off by default — not every service a business offers is necessarily
    // meant to be publicly bookable via the embed widget.
    @Column(name = "bookable_online", nullable = false)
    @Builder.Default
    private boolean bookableOnline = false;

    // How long a booking of this service occupies the schedule for, and how
    // many customers can be booked into the same overlapping window — the
    // two numbers BookingService uses to reject a double-booked slot.
    @Column(name = "duration_minutes", nullable = false)
    @Builder.Default
    private int durationMinutes = 30;

    @Column(name = "max_concurrent_bookings", nullable = false)
    @Builder.Default
    private int maxConcurrentBookings = 1;

    // e.g. home-service or bridal work — the customer must supply where to
    // go when booking this online.
    @Column(name = "requires_location", nullable = false)
    @Builder.Default
    private boolean requiresLocation = false;

    // NONE/DEPOSIT/FULL, or null to fall back to the business's default
    // booking_payment_policy — see BookingService.effectivePolicy().
    @Column(name = "payment_policy_override", length = 20)
    private String paymentPolicyOverride;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
