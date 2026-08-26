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
 * A WooCommerce order mirrored into RBMS via webhook. Deliberately not a
 * Sale — a Sale is an instant, already-complete POS transaction with no
 * status pipeline; a web order needs Received -> Processing -> Ready
 * tracking while a physical item gets made/packed/shipped.
 */
@Entity
@Table(name = "ecommerce_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EcommerceOrder {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "woo_order_id", nullable = false)
    private long wooOrderId;

    @Column(name = "order_number", nullable = false, length = 50)
    private String orderNumber;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "RECEIVED"; // RECEIVED, PROCESSING, READY, COMPLETED, CANCELLED

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    // Links to the same Customer record Sales/Service Orders/Bookings resolve
    // by phone — nullable since WooCommerce billing data may omit a phone
    // entirely. customerName/Email/Phone above stay as the raw Woo snapshot;
    // this is purely the tie.
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "GHS";

    // Full webhook body as raw JSON text, kept for debugging/support without
    // re-fetching from Woo. Plain text, not a mapped jsonb type — nothing
    // queries inside it via SQL, so there's no reason to risk Hibernate's
    // JSON (de)serialization double-encoding a plain String.
    @Column(name = "raw_payload", columnDefinition = "text")
    private String rawPayload;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
