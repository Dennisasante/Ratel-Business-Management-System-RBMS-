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
@Table(name = "ecommerce_order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EcommerceOrderItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "ecommerce_order_id", nullable = false)
    private UUID ecommerceOrderId;

    // Snapshotted, same reasoning as SaleItem.productName — a later product
    // rename must never rewrite a past order's line items.
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
}
