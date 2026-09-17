package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One wig within a staff-logged custom wig request/order — see V62's own
 * migration comment for why this exists alongside (not replacing)
 * CustomWigRequest's own description/estimatedPrice fields, which the public
 * configurator widget still uses exactly as before.
 */
@Entity
@Table(name = "custom_wig_request_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomWigRequestItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
