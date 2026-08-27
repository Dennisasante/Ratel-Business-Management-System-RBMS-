package com.ratel.rbms.controller;

import com.ratel.rbms.service.WhatsAppWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public — Meta itself calls this, no JWT involved (same posture as
 * PaystackWebhookController/WooCommerceWebhookController). Deliberately
 * thin (spec §8): every real decision — verification-token check, signature
 * validation, envelope parsing, binding/module resolution, unsupported-type
 * handling, and handing a normalized message to AiChannelRouter — lives in
 * WhatsAppWebhookService. This controller never queries a customer or
 * booking, never calls the LLM, never executes a tool, and contains no AI
 * prompt or business logic of its own.
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private final WhatsAppWebhookService whatsAppWebhookService;

    public WhatsAppWebhookController(WhatsAppWebhookService whatsAppWebhookService) {
        this.whatsAppWebhookService = whatsAppWebhookService;
    }

    /** Meta's one-time webhook verification handshake — see Meta's "Set up webhooks" docs. */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(value = "hub.challenge", required = false) String challenge
    ) {
        String result = whatsAppWebhookService.handleVerification(mode, verifyToken, challenge);
        return result != null
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /** Every real WhatsApp event (messages, status updates, ...) arrives here. */
    @PostMapping
    public ResponseEntity<Void> receiveEvent(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature
    ) {
        whatsAppWebhookService.handleEvent(rawBody, signature);
        // Always ack 200 once signature verification has passed — Meta
        // retries aggressively on non-2xx, and re-delivering an event we
        // already couldn't/wouldn't process (unknown account, AI disabled,
        // unsupported type, duplicate) will never fix itself on retry.
        return ResponseEntity.ok().build();
    }
}
