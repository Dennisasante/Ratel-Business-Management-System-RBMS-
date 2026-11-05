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

/**
 * One per business. creditBalance is deliberately read-only here — it is only
 * ever changed through SmsAccountRepository's conditional debit/credit
 * queries, so concurrent sends can never lose an update or overspend.
 */
@Entity
@Table(name = "business_sms_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessSmsAccount {

    public static final String SENDER_NONE = "NONE";
    public static final String SENDER_PENDING = "PENDING";
    public static final String SENDER_APPROVED = "APPROVED";

    // How a business's customers get receipts: PAPER only (default), SMS only, or BOTH.
    public static final String RECEIPT_PAPER = "PAPER";
    public static final String RECEIPT_SMS = "SMS";
    public static final String RECEIPT_BOTH = "BOTH";

    @Id
    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "sender_id", length = 11)
    private String senderId;

    @Column(name = "sender_status", nullable = false, length = 20)
    @Builder.Default
    private String senderStatus = SENDER_NONE;

    @Column(name = "receipt_mode", nullable = false, length = 10)
    @Builder.Default
    private String receiptMode = RECEIPT_PAPER;

    @Column(name = "credit_balance", nullable = false, insertable = false, updatable = false)
    private int creditBalance;

    @Column(name = "low_balance_threshold", nullable = false)
    @Builder.Default
    private int lowBalanceThreshold = 20;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean sendsSmsReceipts() {
        return RECEIPT_SMS.equals(receiptMode) || RECEIPT_BOTH.equals(receiptMode);
    }

    public boolean senderApproved() {
        return SENDER_APPROVED.equals(senderStatus) && senderId != null && !senderId.isBlank();
    }
}
