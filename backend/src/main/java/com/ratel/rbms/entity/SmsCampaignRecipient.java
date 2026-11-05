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

import java.time.Instant;
import java.util.UUID;

/** One customer's text within a campaign, and what became of it. */
@Entity
@Table(name = "sms_campaign_recipients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsCampaignRecipient {

    public static final String PENDING = "PENDING";
    public static final String SENDING = "SENDING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(nullable = false)
    private int segments;

    @Column(nullable = false, length = 12)
    @Builder.Default
    private String status = PENDING;

    @Column(name = "provider_ref", length = 100)
    private String providerRef;

    @Column(length = 300)
    private String error;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
