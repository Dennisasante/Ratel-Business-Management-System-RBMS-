package com.ratel.rbms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** The single settings row (id = 1) for Tallia's own SMS economics. */
@Entity
@Table(name = "sms_platform_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SmsPlatformSettings {

    public static final short ROW_ID = 1;

    @Id
    private short id = ROW_ID;

    // What the SMS provider charges Tallia per credit. Null until the Super Admin enters it.
    @Column(name = "provider_cost_per_credit", precision = 10, scale = 4)
    private BigDecimal providerCostPerCredit;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
