package com.ratel.rbms.dto;

// Deliberately minimal and open to every role — unlike BusinessIntegrationsResponse
// (OWNER-only, since it carries masked keys and WooCommerce config), this only
// answers small "can/should this do X right now" questions every receipt page
// needs regardless of who's viewing it (Manager, Sales Person, Accountant, not
// just Owner): can this be charged through Paystack, and should this receipt
// offer/auto-trigger thermal printing and at which paper width.
public record PaymentGatewayStatusResponse(
        boolean paystackConfigured,
        boolean receiptPrinterEnabled,
        String receiptPrinterPaperWidth
) {
}
