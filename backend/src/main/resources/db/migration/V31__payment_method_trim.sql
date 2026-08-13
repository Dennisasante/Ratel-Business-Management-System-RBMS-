-- Payment methods are being trimmed to Cash / Online Payment (MOBILE_MONEY) /
-- Direct Mobile Money (MOBILE_MONEY_DIRECT) — Card and Bank Transfer are
-- removed from the enum. Rewrite any historical rows first so
-- Sale.paymentMethod (an @Enumerated(EnumType.STRING) with no DB CHECK
-- constraint) never hits a value the Java enum no longer recognizes.
-- This only changes how old sales display their payment method, not revenue.
UPDATE sales SET payment_method = 'CASH' WHERE payment_method IN ('CARD', 'BANK_TRANSFER');
