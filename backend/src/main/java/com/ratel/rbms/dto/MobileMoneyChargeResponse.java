package com.ratel.rbms.dto;

// status is Paystack's raw charge status ("success"/"pending"/"send_otp"/"pay_offline");
// displayText is Paystack's own customer-facing instruction when present (e.g. a USSD
// code to dial) — show it to whoever's behind the till so they can relay it if needed.
public record MobileMoneyChargeResponse(String reference, String status, String displayText) {
}
