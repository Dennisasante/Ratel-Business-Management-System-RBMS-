package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Append-only history of every change to a business's credit balance. */
@Entity
@Table(name = "sms_credit_ledger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsCreditLedger {

    public static final String PURCHASE = "PURCHASE";
    public static final String SEND = "SEND";
    public static final String REFUND = "REFUND";
    public static final String ADJUSTMENT = "ADJUSTMENT";
    public static final String CAMPAIGN = "CAMPAIGN";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false)
    private int delta;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(length = 100)
    private String reference;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
