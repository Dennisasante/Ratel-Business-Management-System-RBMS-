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

/** One promotional text sent to many customers. */
@Entity
@Table(name = "sms_campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsCampaign {

    public static final String SENDING = "SENDING";
    public static final String DONE = "DONE";
    public static final String CANCELLED = "CANCELLED";

    public static final String MODE_ALL = "ALL";
    public static final String MODE_SELECTED = "SELECTED";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(length = 80)
    private String name;

    @Column(name = "message_template", nullable = false, columnDefinition = "text")
    private String messageTemplate;

    @Column(name = "audience_mode", nullable = false, length = 10)
    private String audienceMode;

    @Column(name = "include_footer", nullable = false)
    private boolean includeFooter;

    @Column(nullable = false, length = 12)
    @Builder.Default
    private String status = SENDING;

    @Column(name = "recipients_count", nullable = false)
    private int recipientsCount;

    @Column(name = "credits_reserved", nullable = false)
    private int creditsReserved;

    @Column(name = "credits_refunded", nullable = false)
    private int creditsRefunded;

    @Column(name = "request_key", nullable = false)
    private UUID requestKey;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
