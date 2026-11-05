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

/** A credit pack businesses can buy. Platform-wide, Super-Admin-managed; the price carries Tallia's margin over mNotify's own rate. */
@Entity
@Table(name = "sms_bundles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsBundle {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private int credits;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
