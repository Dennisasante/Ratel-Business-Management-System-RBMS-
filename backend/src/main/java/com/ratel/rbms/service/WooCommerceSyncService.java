package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessIntegrations;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.EcommerceOrder;
import com.ratel.rbms.entity.EcommerceOrderItem;
import com.ratel.rbms.entity.Product;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessIntegrationsRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.EcommerceOrderItemRepository;
import com.ratel.rbms.repository.EcommerceOrderRepository;
import com.ratel.rbms.repository.ProductRepository;
import com.ratel.rbms.util.PhoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Both directions of the WooCommerce sync: incoming orders (webhook -> RBMS)
 * and outgoing product/stock pushes (RBMS -> Woo). Product/stock pushes never
 * throw past this class — a client's WooCommerce site being unreachable or
 * misconfigured must never block the RBMS-side operation (creating a
 * product, adjusting stock) that triggered the push; it's logged and
 * swallowed instead, same reasoning as webhook-registration hiccups in
 * BusinessIntegrationsService.
 */
@Service
public class WooCommerceSyncService {

    private static final Logger log = LoggerFactory.getLogger(WooCommerceSyncService.class);

    private final BusinessRepository businessRepository;
    private final BusinessIntegrationsRepository businessIntegrationsRepository;
    private final ProductRepository productRepository;
    private final EcommerceOrderRepository ecommerceOrderRepository;
    private final EcommerceOrderItemRepository ecommerceOrderItemRepository;
    private final WooCommerceClient wooCommerceClient;
    private final PlanFeatureService planFeatureService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final String backendUrl;
    private final CustomerRepository customerRepository;

    public WooCommerceSyncService(
            BusinessRepository businessRepository,
            BusinessIntegrationsRepository businessIntegrationsRepository,
            ProductRepository productRepository,
            EcommerceOrderRepository ecommerceOrderRepository,
            EcommerceOrderItemRepository ecommerceOrderItemRepository,
            WooCommerceClient wooCommerceClient,
            PlanFeatureService planFeatureService,
            EmailService emailService,
            ObjectMapper objectMapper,
            @Value("${app.backend-url}") String backendUrl,
            CustomerRepository customerRepository
    ) {
        this.businessRepository = businessRepository;
        this.businessIntegrationsRepository = businessIntegrationsRepository;
        this.productRepository = productRepository;
        this.ecommerceOrderRepository = ecommerceOrderRepository;
        this.ecommerceOrderItemRepository = ecommerceOrderItemRepository;
        this.wooCommerceClient = wooCommerceClient;
        this.planFeatureService = planFeatureService;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.backendUrl = backendUrl;
        this.customerRepository = customerRepository;
    }

    // Same phone-tying pattern as BookingService/CustomWigRequestService — a
    // Woo order is linked to the same Customer record other modules resolve
    // by phone, rather than an island of its own. Returns null for a blank
    // phone (WooCommerce billing data can omit one entirely).
    private UUID resolveCustomerId(UUID businessId, String customerName, String phone, String email) {
        if (phone == null || phone.isBlank()) return null;
        return customerRepository.findFirstByBusinessIdAndPhoneNormalized(businessId, PhoneUtils.normalize(phone))
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .businessId(businessId)
                        .fullName(customerName == null || customerName.isBlank() ? "Unknown" : customerName)
                        .phone(phone)
                        .email(email)
                        .build()))
                .getId();
    }

    @Transactional
    public void handleIncomingOrder(UUID businessId, String rawBody, String signatureHeader) {
        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));

        if (!wooCommerceClient.verifyWebhookSignature(rawBody, signatureHeader, integrations.getWoocommerceWebhookSecret())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature.");
        }

        // The webhook could still be live from before a plan downgrade — accept
        // it quietly (so Woo doesn't retry-storm a business that just can't use
        // this feature anymore) rather than erroring.
        if (!planFeatureService.hasFeature(businessId, PlanFeature.WOOCOMMERCE_SYNC)) {
            return;
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Malformed webhook payload.");
        }

        // WooCommerce sends a lightweight ping payload when a webhook is first
        // created/re-armed — it has no order fields, just acknowledge it.
        if (!node.hasNonNull("id") || !node.hasNonNull("line_items")) {
            return;
        }

        long wooOrderId = node.get("id").asLong();
        JsonNode billing = node.path("billing");
        String customerName = (billing.path("first_name").asText("") + " " + billing.path("last_name").asText(""))
                .trim();

        var existing = ecommerceOrderRepository.findByBusinessIdAndWooOrderId(businessId, wooOrderId);
        boolean isNew = existing.isEmpty();
        EcommerceOrder order = existing.orElseGet(() -> EcommerceOrder.builder().businessId(businessId).wooOrderId(wooOrderId).build());
        String customerPhone = billing.path("phone").asText(null);
        String customerEmail = billing.path("email").asText(null);
        order.setOrderNumber(node.path("number").asText(String.valueOf(wooOrderId)));
        order.setCustomerName(customerName.isBlank() ? null : customerName);
        order.setCustomerEmail(customerEmail);
        order.setCustomerPhone(customerPhone);
        order.setCustomerId(resolveCustomerId(businessId, customerName, customerPhone, customerEmail));
        order.setTotalAmount(new BigDecimal(node.path("total").asText("0")));
        order.setCurrency(node.path("currency").asText("GHS"));
        order.setRawPayload(rawBody);
        order = ecommerceOrderRepository.save(order);
        ecommerceOrderRepository.flush();

        ecommerceOrderItemRepository.deleteAllByEcommerceOrderId(order.getId());
        for (JsonNode item : node.path("line_items")) {
            int quantity = item.path("quantity").asInt(1);
            BigDecimal unitPrice = new BigDecimal(item.path("total").asText("0"))
                    .divide(BigDecimal.valueOf(Math.max(quantity, 1)), 2, java.math.RoundingMode.HALF_UP);
            UUID productId = null;
            if (item.hasNonNull("product_id")) {
                productId = productRepository.findByBusinessIdAndWooProductId(businessId, item.get("product_id").asLong())
                        .map(Product::getId).orElse(null);
            }
            EcommerceOrderItem orderItem = EcommerceOrderItem.builder()
                    .ecommerceOrderId(order.getId())
                    .productName(item.path("name").asText("Item"))
                    .productId(productId)
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .build();
            ecommerceOrderItemRepository.save(orderItem);
        }

        if (isNew && order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank()) {
            String businessName = businessRepository.findById(businessId).map(Business::getName).orElse("Ratel");
            emailService.sendEcommerceOrderReceived(
                    order.getCustomerEmail(), order.getCustomerName() != null ? order.getCustomerName() : "there",
                    order.getOrderNumber(), businessName
            );
        }
    }

    public void pushProduct(Product product) {
        if (!product.isPublishToWebsite()) {
            return;
        }
        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(product.getBusinessId()).orElse(null);
        if (!wooCommerceConfigured(integrations)) {
            return;
        }

        String imageUrl = absoluteImageUrl(product.getImageUrl());
        try {
            if (product.getWooProductId() == null) {
                long wooId = wooCommerceClient.createProduct(
                        integrations.getWoocommerceSiteUrl(), integrations.getWoocommerceConsumerKey(),
                        integrations.getWoocommerceConsumerSecret(), product.getName(), product.getSellingPrice(),
                        product.getSku(), imageUrl
                );
                product.setWooProductId(wooId);
                productRepository.save(product);
            } else {
                wooCommerceClient.updateProduct(
                        integrations.getWoocommerceSiteUrl(), integrations.getWoocommerceConsumerKey(),
                        integrations.getWoocommerceConsumerSecret(), product.getWooProductId(), product.getName(),
                        product.getSellingPrice(), product.getSku(), imageUrl
                );
            }
        } catch (RestClientException e) {
            log.warn("Couldn't push product {} to WooCommerce for business {}", product.getId(), product.getBusinessId(), e);
        }
    }

    public void pushStockChange(Product product) {
        if (product.getWooProductId() == null) {
            return;
        }
        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(product.getBusinessId()).orElse(null);
        if (!wooCommerceConfigured(integrations)) {
            return;
        }
        try {
            wooCommerceClient.updateStock(
                    integrations.getWoocommerceSiteUrl(), integrations.getWoocommerceConsumerKey(),
                    integrations.getWoocommerceConsumerSecret(), product.getWooProductId(), product.getQuantity()
            );
        } catch (RestClientException e) {
            log.warn("Couldn't push stock for product {} to WooCommerce for business {}", product.getId(), product.getBusinessId(), e);
        }
    }

    private boolean wooCommerceConfigured(BusinessIntegrations integrations) {
        return integrations != null
                && integrations.getWoocommerceSiteUrl() != null && !integrations.getWoocommerceSiteUrl().isBlank()
                && integrations.getWoocommerceConsumerKey() != null && !integrations.getWoocommerceConsumerKey().isBlank()
                && integrations.getWoocommerceConsumerSecret() != null && !integrations.getWoocommerceConsumerSecret().isBlank();
    }

    // Product photos are stored as a relative /uploads/... path — WooCommerce
    // needs an absolute, publicly reachable URL to fetch and import the image.
    private String absoluteImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }
        return backendUrl + imageUrl;
    }
}
