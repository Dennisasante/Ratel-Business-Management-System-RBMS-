package com.ratel.rbms.dto;

public record BusinessIntegrationsResponse(
        String paystackPublicKey,
        boolean paystackSecretConfigured,
        String paystackSecretMasked,
        String woocommerceSiteUrl,
        boolean woocommerceConfigured,
        String woocommerceConsumerKeyMasked,
        boolean woocommerceWebhookRegistered,
        String whatsappNotifyNumber,
        boolean testMode
) {
}
