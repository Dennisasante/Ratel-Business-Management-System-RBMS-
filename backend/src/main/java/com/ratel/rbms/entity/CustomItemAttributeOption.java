package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "custom_item_attribute_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomItemAttributeOption {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "attribute_id", nullable = false)
    private UUID attributeId;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "price_modifier", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal priceModifier = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    // e.g. "Custom Colour" — priceModifier stays informational (typically 0);
    // the customer-facing estimate is labeled "final price confirmed by us"
    // whenever a selected option carries this flag.
    @Column(name = "requires_manual_quote", nullable = false)
    @Builder.Default
    private boolean requiresManualQuote = false;
}
