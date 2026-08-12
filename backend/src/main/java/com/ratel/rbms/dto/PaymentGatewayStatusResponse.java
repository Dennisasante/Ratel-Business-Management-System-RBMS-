package com.ratel.rbms.dto;

// Deliberately minimal — unlike BusinessIntegrationsResponse (OWNER-only, since
// it carries masked keys and WooCommerce config), this is safe for any
// authenticated business role to read: it only answers "can this order/sale be
// charged through Paystack right now," which every receipt page needs regardless
// of who's viewing it (Manager, Sales Person, Accountant, not just Owner).
public record PaymentGatewayStatusResponse(boolean paystackConfigured) {
}
