package com.ratel.rbms.entity.enums;

// Deliberately a separate, smaller enum from PaymentMethod (which also has
// MOBILE_MONEY meaning "charged through Paystack") — an expense is money
// already spent, logged after the fact, so there's no gateway/online option.
public enum ExpensePaymentMethod {
    CASH,
    MOBILE_MONEY
}
