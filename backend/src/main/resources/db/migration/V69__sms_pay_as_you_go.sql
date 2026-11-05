-- Pay-as-you-go SMS credits: a business types an amount and gets amount / price-per-credit
-- credits, instead of choosing a fixed bundle. The price per credit is the Super Admin's
-- selling price; provider_cost_per_credit (V65) stays the basis for profit figures.
ALTER TABLE sms_platform_settings
    ADD COLUMN sell_price_per_credit NUMERIC(10, 4)
        CHECK (sell_price_per_credit IS NULL OR sell_price_per_credit > 0);
