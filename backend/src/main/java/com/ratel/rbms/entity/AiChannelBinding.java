package com.ratel.rbms.entity;

import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.entity.enums.ChannelConnectionMethod;
import com.ratel.rbms.entity.enums.ChannelConnectionState;
import com.ratel.rbms.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
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
 *
 * <p>Phase 5D Stage 0 hardening §5: {@code @DynamicUpdate} — without it, Hibernate's default
 * behaviour is to write EVERY mapped column on every UPDATE, which would let two genuinely
 * concurrent operations touching different fields of the SAME row (e.g. a disconnect setting
 * {@code active=false} racing a health-check test setting {@code lastVerifiedAt}) silently
 * overwrite each other's change with whatever stale value each transaction had loaded — a real,
 * provable lost-update, not a theoretical one (see {@code AiChannelBindingConcurrencyTest}). With
 * {@code @DynamicUpdate}, each transaction's own UPDATE touches only the columns it actually
 * changed, so two concurrent writers to different fields both survive regardless of commit order.
 * Two concurrent writers to the SAME field (e.g. two overlapping test-connection calls) still
 * resolve last-commit-wins, which is correct here — there is no invariant requiring one specific
 * health-check outcome to "win" over another equally-real one. Deliberately NOT optimistic
 * locking ({@code @Version}): that would make either of two legitimate, non-conflicting concurrent
 * operations fail and require a retry, which is unwarranted complexity for connection-health
 * bookkeeping (see the hardening instruction's own "do not over-engineer into a distributed
 * locking framework").
 */
@Entity
@Table(name = "ai_channel_bindings")
@DynamicUpdate
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

    // Phase 5D Stage 0 — how the credentials above were acquired. Purely descriptive; never
    // branched on by AiChannelRouter/AiChannelAdapter/AiChatService.
    @Enumerated(EnumType.STRING)
    @Column(name = "connection_method", nullable = false, length = 20)
    @Builder.Default
    private ChannelConnectionMethod connectionMethod = ChannelConnectionMethod.MANUAL;

    // Phase 5D Stage 0 — set only by a real health check (AiChannelConnectionService.connect/test,
    // or WhatsAppBindingService.testConnection), never speculatively.
    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    // Phase 5D Stage 0 — DERIVED/CACHED from credentialsEncrypted/active/lastVerifiedAt/
    // lastFailureAt. Never set independently of those three signals — see
    // AiChannelConnectionService.deriveState, the sole derivation function both writers share.
    @Enumerated(EnumType.STRING)
    @Column(name = "connection_state", nullable = false, length = 20)
    @Builder.Default
    private ChannelConnectionState connectionState = ChannelConnectionState.NOT_CONNECTED;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
