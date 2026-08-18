package com.ratel.rbms.service;

import com.ratel.rbms.dto.BusinessIntegrationsRequest;
import com.ratel.rbms.dto.BusinessIntegrationsResponse;
import com.ratel.rbms.dto.PaymentGatewayStatusResponse;
import com.ratel.rbms.dto.TestConnectionResponse;
import com.ratel.rbms.entity.BusinessIntegrations;
import com.ratel.rbms.repository.BusinessIntegrationsRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class BusinessIntegrationsService {

    private static final String WOOCOMMERCE_WEBHOOK_TOPIC_CREATED = "order.created";
    private static final String WOOCOMMERCE_WEBHOOK_TOPIC_UPDATED = "order.updated";

    private final BusinessIntegrationsRepository businessIntegrationsRepository;
    private final WooCommerceClient wooCommerceClient;
    private final PlanFeatureService planFeatureService;
    private final String backendUrl;

    public BusinessIntegrationsService(
            BusinessIntegrationsRepository businessIntegrationsRepository,
            WooCommerceClient wooCommerceClient,
            PlanFeatureService planFeatureService,
            @org.springframework.beans.factory.annotation.Value("${app.backend-url}") String backendUrl
    ) {
        this.businessIntegrationsRepository = businessIntegrationsRepository;
        this.wooCommerceClient = wooCommerceClient;
        this.planFeatureService = planFeatureService;
        this.backendUrl = backendUrl;
    }

    public BusinessIntegrationsResponse get() {
        return toResponse(getOrCreate());
    }

    // Safe for any authenticated business role (not OWNER-only) — see
    // PaymentGatewayStatusResponse's own doc comment for why.
    public PaymentGatewayStatusResponse getPaymentGatewayStatus() {
        BusinessIntegrations integrations = getOrCreate();
        boolean configured = integrations.getPaystackSecretKey() != null && !integrations.getPaystackSecretKey().isBlank();
        return new PaymentGatewayStatusResponse(configured);
    }

    @Transactional
    public BusinessIntegrationsResponse update(BusinessIntegrationsRequest req) {
        BusinessIntegrations integrations = getOrCreate();

        if (req.paystackPublicKey() != null) {
            integrations.setPaystackPublicKey(blankToNull(req.paystackPublicKey()));
        }
        if (req.paystackSecretKey() != null) {
            integrations.setPaystackSecretKey(blankToNull(req.paystackSecretKey()));
        }
        if (req.woocommerceSiteUrl() != null) {
            integrations.setWoocommerceSiteUrl(blankToNull(req.woocommerceSiteUrl()));
        }
        if (req.woocommerceConsumerKey() != null) {
            integrations.setWoocommerceConsumerKey(blankToNull(req.woocommerceConsumerKey()));
        }
        if (req.woocommerceConsumerSecret() != null) {
            integrations.setWoocommerceConsumerSecret(blankToNull(req.woocommerceConsumerSecret()));
        }
        if (req.whatsappNotifyNumber() != null) {
            integrations.setWhatsappNotifyNumber(blankToNull(req.whatsappNotifyNumber()));
        }
        if (req.testMode() != null) {
            integrations.setTestMode(req.testMode());
        }
        if (req.notifyOnSale() != null) {
            integrations.setNotifyOnSale(req.notifyOnSale());
        }

        return toResponse(businessIntegrationsRepository.save(integrations));
    }

    public TestConnectionResponse testPaystackConnection() {
        BusinessIntegrations integrations = getOrCreate();
        String secretKey = integrations.getPaystackSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            return new TestConnectionResponse(false, "Save a Paystack secret key first.");
        }

        try {
            // /transaction (list) genuinely requires a valid secret key tied to a
            // real account — unlike /bank, which turned out to serve public
            // reference data regardless of whether the Bearer token is real.
            RestClient.builder()
                    .baseUrl("https://api.paystack.co")
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .build()
                    .get()
                    .uri("/transaction?perPage=1")
                    .retrieve()
                    .toBodilessEntity();
            return new TestConnectionResponse(true, "Connected to Paystack successfully.");
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                return new TestConnectionResponse(false, "That Paystack secret key was rejected — double-check it.");
            }
            return new TestConnectionResponse(false, "Paystack returned an error — please try again.");
        } catch (RestClientException e) {
            return new TestConnectionResponse(false, "Couldn't reach Paystack right now. Please try again.");
        }
    }

    @Transactional
    public TestConnectionResponse testWooCommerceConnection() {
        planFeatureService.requireFeature(PlanFeature.WOOCOMMERCE_SYNC);

        BusinessIntegrations integrations = getOrCreate();
        String siteUrl = integrations.getWoocommerceSiteUrl();
        String consumerKey = integrations.getWoocommerceConsumerKey();
        String consumerSecret = integrations.getWoocommerceConsumerSecret();

        if (siteUrl == null || siteUrl.isBlank() || consumerKey == null || consumerSecret == null) {
            return new TestConnectionResponse(false, "Save the site URL and both API keys first.");
        }

        try {
            wooCommerceClient.verifyConnection(siteUrl, consumerKey, consumerSecret);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                return new TestConnectionResponse(false, "Those WooCommerce API keys were rejected — double-check them.");
            }
            return new TestConnectionResponse(false, "WooCommerce returned an error — double-check the site URL.");
        } catch (RestClientException e) {
            return new TestConnectionResponse(false, "Couldn't reach that site. Double-check the URL.");
        }

        try {
            registerWebhooks(integrations, siteUrl, consumerKey, consumerSecret);
        } catch (RestClientException e) {
            // The connection itself is genuinely valid at this point — a webhook
            // registration hiccup shouldn't be reported as a failed connection.
            return new TestConnectionResponse(true,
                    "Connected, but couldn't register the order webhook automatically. Try again in a moment.");
        }

        businessIntegrationsRepository.save(integrations);
        return new TestConnectionResponse(true, "Connected — order updates will now sync automatically.");
    }

    /**
     * Deletes any webhook already pointing at our own delivery URL for each
     * topic first, then creates fresh — simplest way to stay idempotent
     * across repeated "Test connection" clicks without duplicates piling up.
     */
    private void registerWebhooks(BusinessIntegrations integrations, String siteUrl, String consumerKey, String consumerSecret) {
        String deliveryUrl = backendUrl + "/api/webhooks/woocommerce/" + integrations.getBusinessId();
        String secret = integrations.getWoocommerceWebhookSecret();
        if (secret == null || secret.isBlank()) {
            secret = generateWebhookSecret();
        }

        List<WooCommerceClient.WooWebhook> existing = wooCommerceClient.listWebhooks(siteUrl, consumerKey, consumerSecret);

        for (String topic : List.of(WOOCOMMERCE_WEBHOOK_TOPIC_CREATED, WOOCOMMERCE_WEBHOOK_TOPIC_UPDATED)) {
            existing.stream()
                    .filter(w -> deliveryUrl.equals(w.deliveryUrl()) && topic.equals(w.topic()))
                    .forEach(w -> wooCommerceClient.deleteWebhook(siteUrl, consumerKey, consumerSecret, w.id()));
            wooCommerceClient.createWebhook(siteUrl, consumerKey, consumerSecret, topic, deliveryUrl, secret);
        }

        integrations.setWoocommerceWebhookSecret(secret);
    }

    private String generateWebhookSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private BusinessIntegrations getOrCreate() {
        UUID businessId = TenantContext.getBusinessId();
        return businessIntegrationsRepository.findByBusinessId(businessId)
                .orElseGet(() -> businessIntegrationsRepository.save(
                        BusinessIntegrations.builder().businessId(businessId).build()));
    }

    private String blankToNull(String value) {
        return value.isBlank() ? null : value;
    }

    private BusinessIntegrationsResponse toResponse(BusinessIntegrations i) {
        return new BusinessIntegrationsResponse(
                i.getPaystackPublicKey(),
                i.getPaystackSecretKey() != null,
                mask(i.getPaystackSecretKey()),
                i.getWoocommerceSiteUrl(),
                i.getWoocommerceConsumerKey() != null && i.getWoocommerceConsumerSecret() != null,
                mask(i.getWoocommerceConsumerKey()),
                i.getWoocommerceWebhookSecret() != null,
                i.getWhatsappNotifyNumber(),
                i.isTestMode(),
                i.isNotifyOnSale(),
                i.getPaymentGateway()
        );
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 4) {
            return "••••";
        }
        return "••••" + value.substring(value.length() - 4);
    }
}
