package com.ratel.rbms.dto;

/**
 * Every field follows the same "leave unchanged unless told otherwise"
 * semantics: null = don't touch this field, "" (blank) = clear it, anything
 * else = set it to this new value. Applies uniformly, not just to secrets —
 * simpler than mixing update semantics per field. Secrets are never echoed
 * back by GET /api/integrations, so a save that doesn't intend to change a
 * secret must send null for it, not whatever masked placeholder it saw.
 */
public record BusinessIntegrationsRequest(
        String paystackPublicKey,
        String paystackSecretKey,
        String woocommerceSiteUrl,
        String woocommerceConsumerKey,
        String woocommerceConsumerSecret,
        String whatsappNotifyNumber,
        Boolean testMode
) {
}
