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

/**
 * A bundle of real service_catalog items sold and booked as one atomic unit
 * (e.g. a bridal package: "Bridal Hair + Makeup + 1 Bridesmaid" at a set
 * price). Mirrors ServiceCatalogItem's own bookable shape — own price,
 * duration, capacity, bookableOnline — since a package books as a single
 * appointment slot, not multiple separate bookings. Its component items
 * (ServicePackageItem) are descriptive/structured, not separately tracked
 * line items on the resulting ServiceOrder.
 */
@Entity
@Table(name = "service_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePackage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    // ServiceOrder.serviceTypeId is NOT NULL, so a package needs one too — an
    // owner picks (or creates) something like "Packages" or "Bridal".
    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "bookable_online", nullable = false)
    @Builder.Default
    private boolean bookableOnline = false;

    @Column(name = "duration_minutes", nullable = false)
    @Builder.Default
    private int durationMinutes = 60;

    @Column(name = "max_concurrent_bookings", nullable = false)
    @Builder.Default
    private int maxConcurrentBookings = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
