package com.ratel.rbms.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the WooCommerce REST API. Unlike PaystackService (one
 * fixed platform-wide secret key, one fixed base URL), every call here is
 * against a different business's own site + credentials, so a fresh client
 * is built per call rather than once at startup — this class stays a dumb
 * pipe with no per-business state of its own, same reasoning as PaystackService.
 */
@Service
public class WooCommerceClient {

    /**
     * Base64-encoded HMAC-SHA256 of the raw request body against the
     * per-business webhook secret, compared to WooCommerce's
     * X-WC-Webhook-Signature header — per Woo's webhook docs. Must run
     * against the untouched raw body bytes, before any JSON parsing.
     */
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader, String secret) {
        if (signatureHeader == null || signatureHeader.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(hash);
            return computed.equals(signatureHeader.trim());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    /** Creates a simple product on WooCommerce and returns its Woo product id. */
    public long createProduct(
            String siteUrl, String consumerKey, String consumerSecret,
            String name, BigDecimal price, String sku, String imageUrl
    ) {
        Map<String, Object> body = productBody(name, price, sku, imageUrl);
        WooProduct created = client(siteUrl, consumerKey, consumerSecret)
                .post()
                .uri("/wp-json/wc/v3/products")
                .body(body)
                .retrieve()
                .body(WooProduct.class);
        if (created == null) {
            throw new IllegalStateException("WooCommerce didn't return the created product.");
        }
        return created.id();
    }

    /** Updates name/price/sku/image on an already-linked WooCommerce product. */
    public void updateProduct(
            String siteUrl, String consumerKey, String consumerSecret,
            long wooProductId, String name, BigDecimal price, String sku, String imageUrl
    ) {
        Map<String, Object> body = productBody(name, price, sku, imageUrl);
        client(siteUrl, consumerKey, consumerSecret)
                .put()
                .uri("/wp-json/wc/v3/products/{id}", wooProductId)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /** Pushes just the stock quantity — called far more often than a full product update. */
    public void updateStock(String siteUrl, String consumerKey, String consumerSecret, long wooProductId, int quantity) {
        Map<String, Object> body = Map.of(
                "manage_stock", true,
                "stock_quantity", quantity,
                "in_stock", quantity > 0
        );
        client(siteUrl, consumerKey, consumerSecret)
                .put()
                .uri("/wp-json/wc/v3/products/{id}", wooProductId)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> productBody(String name, BigDecimal price, String sku, String imageUrl) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("regular_price", price != null ? price.toPlainString() : "0");
        body.put("sku", sku);
        if (imageUrl != null && !imageUrl.isBlank()) {
            List<Map<String, String>> images = new ArrayList<>();
            images.add(Map.of("src", imageUrl));
            body.put("images", images);
        }
        return body;
    }

    /** Confirms the site is reachable and the keys are valid. Throws on failure. */
    public void verifyConnection(String siteUrl, String consumerKey, String consumerSecret) {
        client(siteUrl, consumerKey, consumerSecret)
                .get()
                .uri("/wp-json/wc/v3/system_status")
                .retrieve()
                .toBodilessEntity();
    }

    public List<WooWebhook> listWebhooks(String siteUrl, String consumerKey, String consumerSecret) {
        WooWebhook[] webhooks = client(siteUrl, consumerKey, consumerSecret)
                .get()
                .uri("/wp-json/wc/v3/webhooks?per_page=100")
                .retrieve()
                .body(WooWebhook[].class);
        return webhooks == null ? List.of() : List.of(webhooks);
    }

    public void deleteWebhook(String siteUrl, String consumerKey, String consumerSecret, long webhookId) {
        client(siteUrl, consumerKey, consumerSecret)
                .delete()
                .uri("/wp-json/wc/v3/webhooks/{id}?force=true", webhookId)
                .retrieve()
                .toBodilessEntity();
    }

    public WooWebhook createWebhook(
            String siteUrl, String consumerKey, String consumerSecret,
            String topic, String deliveryUrl, String secret
    ) {
        Map<String, Object> body = Map.of(
                "topic", topic,
                "delivery_url", deliveryUrl,
                "secret", secret,
                "status", "active"
        );
        return client(siteUrl, consumerKey, consumerSecret)
                .post()
                .uri("/wp-json/wc/v3/webhooks")
                .body(body)
                .retrieve()
                .body(WooWebhook.class);
    }

    private RestClient client(String siteUrl, String consumerKey, String consumerSecret) {
        String credentials = Base64.getEncoder().encodeToString(
                (consumerKey + ":" + consumerSecret).getBytes(StandardCharsets.UTF_8));
        return RestClient.builder()
                .baseUrl(normalize(siteUrl))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .build();
    }

    private String normalize(String siteUrl) {
        return siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WooWebhook(
            long id,
            String topic,
            String status,
            @JsonProperty("delivery_url") String deliveryUrl
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WooProduct(long id) {
    }
}
