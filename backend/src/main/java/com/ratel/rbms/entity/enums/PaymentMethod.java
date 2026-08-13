package com.ratel.rbms.entity.enums;

public enum PaymentMethod {
    CASH,
    MOBILE_MONEY,
    CARD,
    BANK_TRANSFER,
    // Money sent straight to the business's own mobile money number, with no
    // Paystack involved — for businesses that haven't connected a gateway yet.
    // Treated like CASH: assumed collected the instant the sale is rung up.
    MOBILE_MONEY_DIRECT
}
