package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A custom wig request from the public configurator widget. `selections` is
 * a JSON array (serialized/parsed via Jackson in the service layer, not a
 * mapped Hibernate type) snapshotting the attribute/option labels and price
 * modifiers as they were at submission — editing a pricing rule later must
 * never rewrite a customer's existing quote history.
 */
@Entity
@Table(name = "custom_wig_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomWigRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Generated(GenerationTime.INSERT)
    @Column(name = "request_number", insertable = false, updatable = false)
    private Long requestNumber;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    // Nullable: a staff-entered request (Instagram DM, walk-in) may have only
    // a name and no email on hand. Always set for the public widget, which
    // requires both.
    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_whatsapp", length = 20)
    private String customerWhatsapp;

    // Free text — "Instagram DM", "Walk-in", "Phone call". Null for requests
    // submitted through the public widget, where the channel is implicitly
    // "website" and not worth asking the customer to state.
    @Column(length = 60)
    private String source;

    @Column(nullable = false, columnDefinition = "text")
    private String selections;

    @Column(name = "estimated_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedPrice;

    @Column(name = "inspiration_photo_url")
    private String inspirationPhotoUrl;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "SUBMITTED"; // SUBMITTED, QUOTED, ACCEPTED, DECLINED

    @Column(name = "final_price", precision = 12, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "owner_message", columnDefinition = "text")
    private String ownerMessage;

    @Column(name = "is_test", nullable = false)
    @Builder.Default
    private boolean test = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
