package com.ratel.rbms.controller;

import com.ratel.rbms.service.WooCommerceSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Public — WooCommerce itself calls this, no JWT involved. Authenticity comes
 * from the per-business webhook secret (see WooCommerceSyncService's HMAC
 * check), not from anything in the URL — the businessId path segment is
 * routing information, not a credential.
 */
@RestController
@RequestMapping("/api/webhooks/woocommerce")
public class WooCommerceWebhookController {

    private final WooCommerceSyncService wooCommerceSyncService;

    public WooCommerceWebhookController(WooCommerceSyncService wooCommerceSyncService) {
        this.wooCommerceSyncService = wooCommerceSyncService;
    }

    @PostMapping("/{businessId}")
    public ResponseEntity<Void> receiveOrder(
            @PathVariable UUID businessId,
            @RequestBody String rawBody,
            @RequestHeader(value = "X-WC-Webhook-Signature", required = false) String signature
    ) {
        wooCommerceSyncService.handleIncomingOrder(businessId, rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
