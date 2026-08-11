package com.ratel.rbms.entity;

import com.ratel.rbms.entity.enums.SaleItemType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sale_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "sale_id", nullable = false)
    private UUID saleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 10)
    @Builder.Default
    private SaleItemType itemType = SaleItemType.PRODUCT;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "service_catalog_id")
    private UUID serviceCatalogId;

    // Snapshot of the product/service name at time of sale, so a later
    // rename/delete of either doesn't rewrite history. Kept as productName
    // (not renamed) so this stays a generic display-name column without a
    // mass find-and-replace across every DTO/frontend type that reads it.
    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    // At the attendant's/owner's discretion — knocked off this line only, before subtotal.
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}
