package com.ratel.rbms.entity;

import com.ratel.rbms.util.PhoneUtils;
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
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 50)
    private String phone;

    // Kept in sync automatically (see syncPhoneNormalized() below) rather
    // than only at the one write path that currently exists — defense in
    // depth so any future edit path can't drift out of sync with `phone`.
    @Column(name = "phone_normalized", length = 20)
    private String phoneNormalized;

    @Column(length = 150)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // How this customer found the business — Walk-in/Instagram/WhatsApp/
    // Facebook/Referral/Website/Other, free text like CustomWigRequest.source.
    // Null for customers created before this field existed.
    @Column(length = 30)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void syncPhoneNormalized() {
        this.phoneNormalized = PhoneUtils.normalize(this.phone);
    }
}
