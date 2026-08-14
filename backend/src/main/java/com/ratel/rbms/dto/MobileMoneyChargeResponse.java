package com.ratel.rbms.dto;

// status is Paystack's raw charge status ("success"/"pending"/"send_otp"/"pay_offline"/"failed");
// displayText is Paystack's own customer-facing instruction when present (e.g. a USSD
// code to dial) — show it to whoever's behind the till so they can relay it if needed.
// message is Paystack's own explanation of the outcome — most important on an outright
// rejection (e.g. a first-time payer needing identification on their mobile money
// account), where it's the only clue as to why nothing was sent to the customer's phone.
public record MobileMoneyChargeResponse(String reference, String status, String displayText, String message) {
}
