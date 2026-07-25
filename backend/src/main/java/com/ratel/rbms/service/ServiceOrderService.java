package com.ratel.rbms.service;

import com.ratel.rbms.dto.ServiceOrderRequest;
import com.ratel.rbms.dto.ServiceOrderResponse;
import com.ratel.rbms.dto.ServiceOrderUpdateRequest;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ServiceOrderService {

    private static final int PAGE_SIZE = 50;

    // The fixed line is RECEIVED -> IN_PROGRESS -> COMPLETED -> PICKED_UP; CANCELLED is the
    // one branch off it, reachable from any non-terminal status. PICKED_UP and CANCELLED
    // are terminal — no further transition is allowed once an order lands there.
    private static final Map<ServiceOrderStatus, Set<ServiceOrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            ServiceOrderStatus.RECEIVED, EnumSet.of(ServiceOrderStatus.IN_PROGRESS, ServiceOrderStatus.CANCELLED),
            ServiceOrderStatus.IN_PROGRESS, EnumSet.of(ServiceOrderStatus.COMPLETED, ServiceOrderStatus.CANCELLED),
            ServiceOrderStatus.COMPLETED, EnumSet.of(ServiceOrderStatus.PICKED_UP, ServiceOrderStatus.CANCELLED),
            ServiceOrderStatus.PICKED_UP, EnumSet.noneOf(ServiceOrderStatus.class),
            ServiceOrderStatus.CANCELLED, EnumSet.noneOf(ServiceOrderStatus.class)
    );

    private final ServiceOrderRepository serviceOrderRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final CustomerService customerService;
    private final ServiceCatalogService serviceCatalogService;
    private final ServiceTypeService serviceTypeService;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;

    public ServiceOrderService(
            ServiceOrderRepository serviceOrderRepository,
            UserRepository userRepository,
            BusinessRepository businessRepository,
            CustomerService customerService,
            ServiceCatalogService serviceCatalogService,
            ServiceTypeService serviceTypeService,
            EmailService emailService,
            ActivityLogService activityLogService
    ) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.customerService = customerService;
        this.serviceCatalogService = serviceCatalogService;
        this.serviceTypeService = serviceTypeService;
        this.emailService = emailService;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public ServiceOrderResponse create(ServiceOrderRequest req) {
        UUID businessId = TenantContext.getBusinessId();

        ServiceType type = serviceTypeService.getOwned(req.serviceTypeId());

        Customer customer = null;
        if (req.customerId() != null) {
            customer = customerService.getOwned(req.customerId());
        }

        ServiceCatalogItem catalogItem = null;
        BigDecimal price = req.price();
        if (req.serviceCatalogId() != null) {
            catalogItem = serviceCatalogService.getOwned(req.serviceCatalogId());
            if (price == null) price = catalogItem.getPrice();
        }
        if (price == null) price = BigDecimal.ZERO;
        BigDecimal discountAmount = req.discountAmount() != null ? req.discountAmount() : BigDecimal.ZERO;

        ServiceOrder order = ServiceOrder.builder()
                .businessId(businessId)
                .serviceTypeId(type.getId())
                .status(ServiceOrderStatus.RECEIVED)
                .customerId(req.customerId())
                .serviceCatalogId(req.serviceCatalogId())
                .notes(req.notes())
                .price(price)
                .discountAmount(discountAmount)
                .assignedStaffId(req.assignedStaffId())
                .receivedAt(Instant.now())
                .scheduledAt(req.scheduledAt())
                .createdBy(TenantContext.getUserId())
                .build();
        order = serviceOrderRepository.save(order);
        serviceOrderRepository.flush(); // so order_number is readable below

        activityLogService.log(
                "Created service order #" + order.getOrderNumber() + " (" + type.getName() + ") for GH₵" + price,
                "SERVICE_ORDER", order.getId()
        );

        return toResponse(order, type, customer, catalogItem);
    }

    public List<ServiceOrderResponse> list(UUID serviceTypeId, ServiceOrderStatus status, int page) {
        UUID businessId = TenantContext.getBusinessId();
        List<ServiceOrder> orders = serviceOrderRepository.search(businessId, serviceTypeId, status, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        return orders.stream().map(this::toResponse).toList();
    }

    public ServiceOrderResponse get(UUID id) {
        return toResponse(getOwned(id));
    }

    @Transactional
    public ServiceOrderResponse update(UUID id, ServiceOrderUpdateRequest req) {
        ServiceOrder order = getOwned(id);
        order.setNotes(req.notes());
        order.setPrice(req.price());
        order.setDiscountAmount(req.discountAmount() != null ? req.discountAmount() : BigDecimal.ZERO);
        order.setAssignedStaffId(req.assignedStaffId());
        order.setScheduledAt(req.scheduledAt());
        order = serviceOrderRepository.save(order);

        activityLogService.log("Updated service order #" + order.getOrderNumber(), "SERVICE_ORDER", order.getId());

        return toResponse(order);
    }

    @Transactional
    public ServiceOrderResponse updateStatus(UUID id, ServiceOrderStatus newStatus) {
        ServiceOrder order = getOwned(id);
        ServiceOrderStatus current = order.getStatus();

        if (current == newStatus) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This order is already " + newStatus.name().toLowerCase() + ".");
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(newStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Can't move a " + current.name().toLowerCase() + " order straight to " + newStatus.name().toLowerCase() + ".");
        }

        order.setStatus(newStatus);
        if (newStatus == ServiceOrderStatus.PICKED_UP) {
            order.setPickedUpAt(Instant.now());
        }
        order = serviceOrderRepository.save(order);

        activityLogService.log(
                "Marked service order #" + order.getOrderNumber() + " as " + newStatus.name(),
                "SERVICE_ORDER", order.getId()
        );

        if (newStatus == ServiceOrderStatus.COMPLETED) {
            sendReadyEmailIfPossible(order);
        }

        return toResponse(order);
    }

    // Deliberately unguarded by status — the manual "Resend" button on an order should
    // work regardless of where the order currently stands.
    @Transactional
    public ServiceOrderResponse resendReadyEmail(UUID id) {
        ServiceOrder order = getOwned(id);
        Customer customer = customerService.getOrNull(order.getCustomerId());
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This order has no customer email on file to send to.");
        }

        Business business = businessRepository.findById(order.getBusinessId()).orElse(null);
        String businessName = business != null ? business.getName() : "Ratel";
        emailService.sendServiceOrderReady(customer.getEmail(), customer.getFullName(), order.getOrderNumber(), businessName);

        order.setReadyEmailSentAt(Instant.now());
        order = serviceOrderRepository.save(order);

        activityLogService.log("Resent ready email for service order #" + order.getOrderNumber(), "SERVICE_ORDER", order.getId());

        return toResponse(order);
    }

    private void sendReadyEmailIfPossible(ServiceOrder order) {
        Customer customer = customerService.getOrNull(order.getCustomerId());
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            return; // no email on file — the manual Resend button remains available once one is added
        }

        Business business = businessRepository.findById(order.getBusinessId()).orElse(null);
        String businessName = business != null ? business.getName() : "Ratel";
        emailService.sendServiceOrderReady(customer.getEmail(), customer.getFullName(), order.getOrderNumber(), businessName);

        order.setReadyEmailSentAt(Instant.now());
        serviceOrderRepository.save(order);
    }

    private ServiceOrder getOwned(UUID id) {
        return serviceOrderRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service order not found."));
    }

    private ServiceOrderResponse toResponse(ServiceOrder order) {
        ServiceType type = findTypeOrNull(order.getServiceTypeId());
        Customer customer = customerService.getOrNull(order.getCustomerId());
        ServiceCatalogItem catalogItem = order.getServiceCatalogId() != null
                ? findCatalogItemOrNull(order.getServiceCatalogId())
                : null;
        return toResponse(order, type, customer, catalogItem);
    }

    private ServiceType findTypeOrNull(UUID id) {
        try {
            return serviceTypeService.getOwned(id);
        } catch (ApiException e) {
            return null; // service type may have been removed since the order was created
        }
    }

    private ServiceCatalogItem findCatalogItemOrNull(UUID id) {
        try {
            return serviceCatalogService.getOwned(id);
        } catch (ApiException e) {
            return null; // catalog item may have been removed since the order was created
        }
    }

    private ServiceOrderResponse toResponse(ServiceOrder order, ServiceType type, Customer customer, ServiceCatalogItem catalogItem) {
        String assignedStaffName = order.getAssignedStaffId() != null
                ? userRepository.findById(order.getAssignedStaffId()).map(User::getFullName).orElse(null)
                : null;
        String createdByName = order.getCreatedBy() != null
                ? userRepository.findById(order.getCreatedBy()).map(User::getFullName).orElse("Unknown")
                : "Unknown";

        return ServiceOrderResponse.from(
                order,
                type != null ? type.getName() : null,
                customer != null ? customer.getFullName() : null,
                catalogItem != null ? catalogItem.getName() : null,
                assignedStaffName,
                createdByName
        );
    }
}
