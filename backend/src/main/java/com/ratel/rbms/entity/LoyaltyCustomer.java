package com.ratel.rbms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One customer's standing in a business's program. The counters are read-only here: they
 * only ever change through LoyaltyCustomerRepository's atomic statements.
 */
@Entity
@Table(name = "loyalty_customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyCustomer {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    // The unguessable part of the customer's personal points-page link.
    @Column(nullable = false, unique = true, length = 16)
    private String token;

    @Column(name = "visits_total", nullable = false, insertable = false, updatable = false)
    private int visitsTotal;

    @Column(name = "visits_redeemed", nullable = false, insertable = false, updatable = false)
    private int visitsRedeemed;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
