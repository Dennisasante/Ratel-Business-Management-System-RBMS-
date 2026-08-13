package com.ratel.rbms.entity.enums;

public enum PaymentMethod {
    CASH,
    // Online Payment — charged through Paystack (phone-push/OTP flow).
    MOBILE_MONEY,
    // Direct Mobile Money — sent straight to the business's own mobile money
    // number, with no Paystack involved. Treated like CASH: assumed collected
    // the instant the sale is rung up.
    MOBILE_MONEY_DIRECT
}
