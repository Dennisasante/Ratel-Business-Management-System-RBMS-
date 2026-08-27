package com.ratel.rbms.entity;

import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.security.EncryptedStringConverter;
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
 * One row per external channel identity a business has connected — a
 * WhatsApp phone-number-ID, an Instagram/Facebook page, etc. Phase 3A never
 * creates a real one of these (no external credentials exist yet); this
 * entity exists so a future channel adapter has somewhere to resolve
 * "which business does this external account belong to" without any AI-core
 * code needing to change.
 *
 * Uniqueness on (channel, externalAccountId) is deliberately GLOBAL, not
 * per-business — see V50__ai_channel_foundation.sql's own comment. That
 * constraint is what makes "ambiguous routing must be rejected" a database
 * guarantee rather than an application-level judgment call.
 *
 * credentialsEncrypted reuses the exact same AES-256-GCM converter
 * BusinessIntegrations already uses for Paystack/WooCommerce secrets — no
 * second encryption scheme for channel credentials.
 */
@Entity
@Table(name = "ai_channel_bindings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChannelBinding {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiChannel channel;

    // The external platform's own account identifier (e.g. a WhatsApp
    // phone-number-ID or Meta page id) — what makes this binding globally
    // unique for its channel. Null only makes sense for a channel that has
    // no such concept (nothing today; kept nullable for forward safety).
    @Column(name = "external_account_id", length = 200)
    private String externalAccountId;

    // A secondary channel-specific identifier some channels need alongside
    // externalAccountId (deliberately generic — covers "sender phone number"
    // for a WhatsApp business that fields multiple numbers, or similar).
    @Column(name = "external_sender_id", length = 200)
    private String externalSenderId;

    @Column(name = "display_name", length = 200)
    private String displayName;

    // Never a real value yet — Phase 3A creates no external credentials.
    // See EncryptedStringConverter; never logged, never returned in a plain
    // API response.
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "credentials_encrypted", columnDefinition = "text")
    private String credentialsEncrypted;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
