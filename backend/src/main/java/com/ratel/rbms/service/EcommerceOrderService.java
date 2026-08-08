package com.ratel.rbms.service;

import com.ratel.rbms.dto.EcommerceOrderDetailResponse;
import com.ratel.rbms.dto.EcommerceOrderItemResponse;
import com.ratel.rbms.dto.EcommerceOrderResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.EcommerceOrder;
import com.ratel.rbms.entity.EcommerceOrderItem;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.EcommerceOrderItemRepository;
import com.ratel.rbms.repository.EcommerceOrderRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Status pipeline for orders synced in from WooCommerce — same fixed-line
 * shape as ServiceOrderService's own ALLOWED_TRANSITIONS: RECEIVED ->
 * PROCESSING -> READY -> COMPLETED, with CANCELLED branching off any
 * non-terminal status. Plain strings rather than an enum on the entity
 * (matches EcommerceOrder.status), so the map is keyed by string too.
 */
@Service
public class EcommerceOrderService {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "RECEIVED", Set.of("PROCESSING", "CANCELLED"),
            "PROCESSING", Set.of("READY", "CANCELLED"),
            "READY", Set.of("COMPLETED", "CANCELLED"),
            "COMPLETED", Set.of(),
            "CANCELLED", Set.of()
    );

    private static final Map<String, String> STATUS_MESSAGES = Map.of(
            "PROCESSING", "Your order is now being processed.",
            "READY", "Your order is ready!",
            "COMPLETED", "Your order is complete. Thank you for shopping with us!",
            "CANCELLED", "Your order has been cancelled."
    );

    private final EcommerceOrderRepository ecommerceOrderRepository;
    private final EcommerceOrderItemRepository ecommerceOrderItemRepository;
    private final BusinessRepository businessRepository;
    private final PlanFeatureService planFeatureService;
    private final EmailService emailService;
    private final WhatsAppLinkService whatsAppLinkService;
    private final ActivityLogService activityLogService;

    public EcommerceOrderService(
            EcommerceOrderRepository ecommerceOrderRepository,
            EcommerceOrderItemRepository ecommerceOrderItemRepository,
            BusinessRepository businessRepository,
            PlanFeatureService planFeatureService,
            EmailService emailService,
            WhatsAppLinkService whatsAppLinkService,
            ActivityLogService activityLogService
    ) {
        this.ecommerceOrderRepository = ecommerceOrderRepository;
        this.ecommerceOrderItemRepository = ecommerceOrderItemRepository;
        this.businessRepository = businessRepository;
        this.planFeatureService = planFeatureService;
        this.emailService = emailService;
        this.whatsAppLinkService = whatsAppLinkService;
        this.activityLogService = activityLogService;
    }

    public List<EcommerceOrderResponse> list() {
        UUID businessId = TenantContext.getBusinessId();
        planFeatureService.requireFeature(businessId, PlanFeature.WOOCOMMERCE_SYNC);
        return ecommerceOrderRepository.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(this::toResponse)
                .toList();
    }

    public EcommerceOrderDetailResponse get(UUID id) {
        EcommerceOrder order = getOwned(id);
        List<EcommerceOrderItemResponse> items = ecommerceOrderItemRepository.findAllByEcommerceOrderId(order.getId()).stream()
                .map(EcommerceOrderItemResponse::from)
                .toList();
        return new EcommerceOrderDetailResponse(
                order.getId(), order.getOrderNumber(), order.getStatus(), order.getCustomerName(),
                order.getCustomerEmail(), order.getCustomerPhone(), order.getTotalAmount(), order.getCurrency(),
                whatsappLinkFor(order), items, order.getCreatedAt(), order.getUpdatedAt()
        );
    }

    @Transactional
    public EcommerceOrderResponse updateStatus(UUID id, String newStatus) {
        EcommerceOrder order = getOwned(id);
        String current = order.getStatus();

        if (current.equals(newStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This order is already " + newStatus.toLowerCase() + ".");
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(newStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Can't move a " + current.toLowerCase() + " order straight to " + newStatus.toLowerCase() + ".");
        }

        order.setStatus(newStatus);
        order = ecommerceOrderRepository.save(order);

        activityLogService.log(
                "Marked order #" + order.getOrderNumber() + " as " + newStatus,
                "ECOMMERCE_ORDER", order.getId()
        );

        String statusMessage = STATUS_MESSAGES.get(newStatus);
        if (statusMessage != null && order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank()) {
            String businessName = businessRepository.findById(order.getBusinessId()).map(Business::getName).orElse("Ratel");
            emailService.sendEcommerceOrderStatusUpdate(
                    order.getCustomerEmail(), order.getCustomerName() != null ? order.getCustomerName() : "there",
                    order.getOrderNumber(), businessName, statusMessage
            );
        }

        return toResponse(order);
    }

    private EcommerceOrder getOwned(UUID id) {
        UUID businessId = TenantContext.getBusinessId();
        planFeatureService.requireFeature(businessId, PlanFeature.WOOCOMMERCE_SYNC);
        return ecommerceOrderRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found."));
    }

    private EcommerceOrderResponse toResponse(EcommerceOrder order) {
        int itemCount = ecommerceOrderItemRepository.findAllByEcommerceOrderId(order.getId()).size();
        return new EcommerceOrderResponse(
                order.getId(), order.getOrderNumber(), order.getStatus(), order.getCustomerName(),
                order.getCustomerEmail(), order.getCustomerPhone(), order.getTotalAmount(), order.getCurrency(),
                itemCount, whatsappLinkFor(order), order.getCreatedAt(), order.getUpdatedAt()
        );
    }

    private String whatsappLinkFor(EcommerceOrder order) {
        String message = STATUS_MESSAGES.getOrDefault(order.getStatus(), "Here's an update on your order.");
        String fullMessage = "Hi " + (order.getCustomerName() != null ? order.getCustomerName() : "there") + ", "
                + message + " (Order #" + order.getOrderNumber() + ")";
        return whatsAppLinkService.buildLink(order.getCustomerPhone(), fullMessage);
    }
}
