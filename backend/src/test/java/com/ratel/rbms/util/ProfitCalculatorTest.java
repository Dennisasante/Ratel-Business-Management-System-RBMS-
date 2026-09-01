package com.ratel.rbms.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProfitCalculatorTest {

    @Test
    void profitIsSellingPriceMinusCost() {
        assertEquals(new BigDecimal("20"), ProfitCalculator.profit(new BigDecimal("100"), new BigDecimal("80")));
    }

    @Test
    void marginIsProfitOverDenominatorTimesHundred() {
        // Product-list-price case from the spec: cost 80, selling 100 -> 20% margin.
        BigDecimal profit = ProfitCalculator.profit(new BigDecimal("100"), new BigDecimal("80"));
        assertEquals(new BigDecimal("20.00"), ProfitCalculator.marginPercent(profit, new BigDecimal("100")));
    }

    @Test
    void discountedSaleUsesActualRevenueNotListPrice() {
        // The spec's own worked example: cost 80, list price 100, customer
        // actually pays 90 after a discount -> profit 10, margin 11.11%.
        BigDecimal revenue = new BigDecimal("90");
        BigDecimal cogs = new BigDecimal("80");
        BigDecimal profit = ProfitCalculator.profit(revenue, cogs);
        assertEquals(new BigDecimal("10"), profit);
        assertEquals(new BigDecimal("11.11"), ProfitCalculator.marginPercent(profit, revenue));
    }

    @Test
    void unknownCostProducesNullNeverAFabricatedZero() {
        assertNull(ProfitCalculator.profit(new BigDecimal("100"), null));
        assertNull(ProfitCalculator.marginPercent(null, new BigDecimal("100")));
    }

    @Test
    void zeroOrNegativeDenominatorNeverDividesByZero() {
        BigDecimal profit = new BigDecimal("10");
        assertNull(ProfitCalculator.marginPercent(profit, BigDecimal.ZERO));
        assertNull(ProfitCalculator.marginPercent(profit, new BigDecimal("-5")));
    }
}
