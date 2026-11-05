package com.ratel.rbms.entity;

import com.ratel.rbms.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

    // What a business pays Tallia per credit when it buys by amount. Null until the Super Admin sets it.
    @Column(name = "sell_price_per_credit", precision = 10, scale = 4)
    private BigDecimal sellPricePerCredit;

    // The provider's API key, saved from the Super Admin SMS page. Encrypted at rest; never sent back to the browser.
    // Takes priority over the server's MNOTIFY_API_KEY, which remains as the fallback.
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "mnotify_api_key")
    private String mnotifyApiKey;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
