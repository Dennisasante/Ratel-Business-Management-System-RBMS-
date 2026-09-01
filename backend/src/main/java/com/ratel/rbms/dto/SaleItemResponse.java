package com.ratel.rbms.dto;

import com.ratel.rbms.entity.SaleItem;
import com.ratel.rbms.entity.enums.SaleItemType;
import com.ratel.rbms.util.ProfitCalculator;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemResponse(
        UUID id,
        SaleItemType itemType,
        UUID productId,
        UUID serviceCatalogId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal discountAmount,
        BigDecimal subtotal,
        boolean gift,
        // Everything below is null for a SERVICE line, or for a PRODUCT line
        // sold before cost tracking existed and never backfilled — never a
        // fabricated 0, so the UI can tell "no profit" apart from "unknown."
        BigDecimal unitCost,
        BigDecimal costOfGoodsSold,
        BigDecimal grossProfit,
        BigDecimal grossMarginPercent
) {
    public static SaleItemResponse from(SaleItem item) {
        BigDecimal cogs = item.getUnitCost() != null
                ? item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity()))
                : null;
        // Gross profit/margin are based on the ACTUAL realized revenue for
        // this line (subtotal, already net of any discount) — never the list
        // price — so a discounted sale correctly shows a smaller profit,
        // per the spec's own worked example (cost 80, list 100, paid 90 ->
        // profit 10, margin 11.11%, not 20/20%).
        BigDecimal grossProfit = ProfitCalculator.profit(item.getSubtotal(), cogs);
        BigDecimal grossMarginPercent = ProfitCalculator.marginPercent(grossProfit, item.getSubtotal());

        return new SaleItemResponse(
                item.getId(),
                item.getItemType(),
                item.getProductId(),
                item.getServiceCatalogId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getDiscountAmount(),
                item.getSubtotal(),
                item.isGift(),
                item.getUnitCost(),
                cogs,
                grossProfit,
                grossMarginPercent
        );
    }
}
