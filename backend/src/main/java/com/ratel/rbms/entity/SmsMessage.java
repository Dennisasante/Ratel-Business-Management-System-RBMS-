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

@Entity
@Table(name = "sms_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsMessage {

    // kind: why it was sent. Only TEST is used by the foundation; LOYALTY and
    // CAMPAIGN arrive with the features that send them.
    public static final String KIND_TEST = "TEST";
    public static final String KIND_TRANSACTIONAL = "TRANSACTIONAL";
    // A customer's receipt for a completed sale — always sent (it isn't marketing).
    public static final String KIND_RECEIPT = "RECEIPT";
    public static final String KIND_LOYALTY = "LOYALTY";
    public static final String KIND_CAMPAIGN = "CAMPAIGN";

    public static final String QUEUED = "QUEUED";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false, length = 20)
    private String recipient;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    // What this message is about (e.g. SALE + the sale's id for a receipt).
    @Column(name = "source_type", length = 20)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "sender_id", nullable = false, length = 11)
    private String senderId;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false)
    private int segments;

    @Column(name = "credits_used", nullable = false)
    private int creditsUsed;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "provider_ref", length = 100)
    private String providerRef;

    @Column(columnDefinition = "text")
    private String error;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
