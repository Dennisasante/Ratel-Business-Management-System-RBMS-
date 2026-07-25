package com.ratel.rbms.entity;

import com.ratel.rbms.entity.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sale {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    // DB-assigned via BIGSERIAL. @Generated tells Hibernate to select it back
    // right after insert, since it's not the entity's @Id and JPA won't refresh
    // non-key generated columns on its own.
    @Generated(GenerationTime.INSERT)
    @Column(name = "sale_number", insertable = false, updatable = false)
    private Long saleNumber;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "cashier_id")
    private UUID cashierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // Snapshotted at sale creation from the cashier's commission_rate at that
    // moment — deliberately not recalculated later, so a rate change never
    // retroactively alters what someone already earned on a past sale.
    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
