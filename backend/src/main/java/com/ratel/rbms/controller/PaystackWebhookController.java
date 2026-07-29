package com.ratel.rbms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratel.rbms.service.BillingService;
import com.ratel.rbms.service.PaystackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redundant source of truth alongside the client-triggered /billing/verify —
 * both funnel into the same BillingService.verifyPayment(reference), which is
 * safe to call from either or both, in any order, any number of times (see the
 * idempotency guard there).
 */
@RestController
@RequestMapping("/api/webhooks/paystack")
public class PaystackWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaystackWebhookController.class);

    private final PaystackService paystackService;
    private final BillingService billingService;
    private final ObjectMapper objectMapper;

    public PaystackWebhookController(PaystackService paystackService, BillingService billingService, ObjectMapper objectMapper) {
        this.paystackService = paystackService;
        this.billingService = billingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> handle(@RequestBody String rawBody, @RequestHeader("x-paystack-signature") String signature) {
        if (!paystackService.verifyWebhookSignature(rawBody, signature)) {
            log.warn("Rejected a Paystack webhook with an invalid signature.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("event").asText();
            if ("charge.success".equals(event)) {
                String reference = root.path("data").path("reference").asText(null);
                if (reference != null) {
                    billingService.verifyPayment(reference);
                }
            }
        } catch (Exception e) {
            // Always ack with 200 below regardless — Paystack retries aggressively
            // on non-2xx, and re-delivering a webhook we already failed to parse
            // once won't fix itself. Logged so a real problem doesn't go unnoticed.
            log.error("Failed to process Paystack webhook payload", e);
        }

        return ResponseEntity.ok().build();
    }
}
