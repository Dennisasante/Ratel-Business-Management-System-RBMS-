package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * The booking-widget-specific wrapper around a real ServiceOrder — the order
 * itself is what staff actually work from (list, calendar, status pipeline);
 * this just carries the public-booking concerns (customer contact, the
 * no-login manage link, payment) that don't belong on ServiceOrder itself.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Generated(GenerationTime.INSERT)
    @Column(name = "booking_number", insertable = false, updatable = false)
    private Long bookingNumber;

    @Column(name = "service_order_id", nullable = false)
    private UUID serviceOrderId;

    // Only set for a staff-entered booking against an existing Customer
    // record — the public widget always upserts by phone but never links
    // this column, so it stays the source of truth only for that path.
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    // Nullable: a staff-entered booking may have only a name on hand.
    // Always set for the public widget, which requires both.
    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_whatsapp", length = 20)
    private String customerWhatsapp;

    // Only set when the booked service/package requires it (home-service, bridal work).
    @Column(name = "customer_location", columnDefinition = "TEXT")
    private String customerLocation;

    // Reschedule/cancel without an account — see spec's "no customer login" decision.
    @Column(name = "manage_token", nullable = false, unique = true, length = 64)
    private String manageToken;

    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private String paymentStatus = "UNPAID"; // UNPAID, PAID, FAILED, PAY_IN_PERSON

    @Column(name = "paystack_reference", unique = true, length = 100)
    private String paystackReference;

    @Column(name = "is_test", nullable = false)
    @Builder.Default
    private boolean test = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
