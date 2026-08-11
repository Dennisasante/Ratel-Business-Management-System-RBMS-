package com.ratel.rbms.service;

import com.ratel.rbms.dto.ServiceOrderRequest;
import com.ratel.rbms.dto.ServiceOrderResponse;
import com.ratel.rbms.dto.ServiceOrderUpdateRequest;
import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BookingRepository;
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

    // The fixed line is RECEIVED -> IN_PROGRESS -> COMPLETED -> PICKED_UP; CANCELLED is a
    // branch off any non-terminal status. Each forward edge also has its one-step-back
    // reverse (IN_PROGRESS->RECEIVED, COMPLETED->IN_PROGRESS, PICKED_UP->COMPLETED) plus
    // CANCELLED->RECEIVED as a "reopen" — so a mistaken status change is always correctable
    // without allowing an arbitrary jump (e.g. RECEIVED straight to PICKED_UP), which would
    // make the pipeline meaningless. Every change here (forward or back) is already logged
    // unconditionally below, so a correction is automatically an audit trail entry.
    private static final Map<ServiceOrderStatus, Set<ServiceOrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            ServiceOrderStatus.RECEIVED, EnumSet.of(ServiceOrderStatus.IN_PROGRESS, ServiceOrderStatus.CANCELLED),
            ServiceOrderStatus.IN_PROGRESS, EnumSet.of(ServiceOrderStatus.RECEIVED, ServiceOrderStatus.COMPLETED, ServiceOrderStatus.CANCELLED),
            ServiceOrderStatus.COMPLETED, EnumSet.of(ServiceOrderStatus.IN_PROGRESS, ServiceOrderStatus.PICKED_UP, ServiceOrderStatus.CANCELLED),
            ServiceOrderStatus.PICKED_UP, EnumSet.of(ServiceOrderStatus.COMPLETED),
            ServiceOrderStatus.CANCELLED, EnumSet.of(ServiceOrderStatus.RECEIVED)
    );

    private final ServiceOrderRepository serviceOrderRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final CustomerService customerService;
    private final ServiceCatalogService serviceCatalogService;
    private final ServiceTypeService serviceTypeService;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;
    private final BookingRepository bookingRepository;
    private final WhatsAppLinkService whatsAppLinkService;

    public ServiceOrderService(
            ServiceOrderRepository serviceOrderRepository,
            UserRepository userRepository,
            BusinessRepository businessRepository,
            CustomerService customerService,
            ServiceCatalogService serviceCatalogService,
            ServiceTypeService serviceTypeService,
            EmailService emailService,
            ActivityLogService activityLogService,
            BookingRepository bookingRepository,
            WhatsAppLinkService whatsAppLinkService
    ) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.customerService = customerService;
        this.serviceCatalogService = serviceCatalogService;
        this.serviceTypeService = serviceTypeService;
        this.emailService = emailService;
        this.activityLogService = activityLogService;
        this.bookingRepository = bookingRepository;
        this.whatsAppLinkService = whatsAppLinkService;
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

        // A STAFF user can't hand work to someone else — every order they create
        // lands on themselves, regardless of what the form submitted. Also keeps
        // the order from becoming invisible to its own creator once list()/get()
        // start scoping STAFF to only their own assigned orders, below.
        User currentUser = currentUser();
        UUID assignedStaffId = currentUser.getRole() == Role.STAFF ? currentUser.getId() : req.assignedStaffId();

        ServiceOrder order = ServiceOrder.builder()
                .businessId(businessId)
                .serviceTypeId(type.getId())
                .status(ServiceOrderStatus.RECEIVED)
                .customerId(req.customerId())
                .serviceCatalogId(req.serviceCatalogId())
                .notes(req.notes())
                .price(price)
                .discountAmount(discountAmount)
                .assignedStaffId(assignedStaffId)
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
        UUID staffScope = currentUser().getRole() == Role.STAFF ? TenantContext.getUserId() : null;
        List<ServiceOrder> orders = serviceOrderRepository.search(
                businessId, serviceTypeId, status, staffScope, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        return orders.stream().map(this::toResponse).toList();
    }

    public ServiceOrderResponse get(UUID id) {
        return toResponse(getOwned(id));
    }

    @Transactional
    public ServiceOrderResponse update(UUID id, ServiceOrderUpdateRequest req) {
        ServiceOrder order = getOwned(id);
        User currentUser = currentUser();
        // A STAFF user can only ever reassign an order to themselves — never hand
        // it off to someone else, and never orphan it (which would make it
        // invisible to everyone once it has no owner in their own scoped view).
        UUID assignedStaffId = currentUser.getRole() == Role.STAFF ? currentUser.getId() : req.assignedStaffId();
        order.setNotes(req.notes());
        order.setPrice(req.price());
        order.setDiscountAmount(req.discountAmount() != null ? req.discountAmount() : BigDecimal.ZERO);
        order.setAssignedStaffId(assignedStaffId);
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
        } else if (current == ServiceOrderStatus.PICKED_UP) {
            // Moving back off PICKED_UP (a correction) — the old timestamp no
            // longer describes this order's actual state, so clear it rather
            // than leave a stale "picked up at" on an order that isn't anymore.
            order.setPickedUpAt(null);
        }
        order = serviceOrderRepository.save(order);

        activityLogService.log(
                "Moved service order #" + order.getOrderNumber() + " from " + current.name() + " to " + newStatus.name(),
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
        ServiceOrder order = serviceOrderRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service order not found."));
        User currentUser = currentUser();
        // 404, not 403 — a STAFF user shouldn't be able to tell an order belonging
        // to someone else even exists.
        if (currentUser.getRole() == Role.STAFF && !currentUser.getId().equals(order.getAssignedStaffId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Service order not found.");
        }
        return order;
    }

    private User currentUser() {
        return userRepository.findById(TenantContext.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));
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
        Booking booking = bookingRepository.findByServiceOrderId(order.getId()).orElse(null);
        String bookingPaymentStatus = booking != null ? booking.getPaymentStatus() : null;
        String bookingWhatsappLink = booking != null
                ? whatsAppLinkService.buildLink(booking.getCustomerWhatsapp(),
                        "Hi " + booking.getCustomerName() + ", this is regarding your booking #" + booking.getBookingNumber() + ".")
                : null;

        // Independent of bookingWhatsappLink above (booking-originated orders only) —
        // this works for every order that has a customer with a phone on file, which
        // is the fallback that was missing: sendReadyEmailIfPossible() silently no-ops
        // with no email, and until now there was nothing else to notify the customer with.
        String customerWhatsappLink = customer != null && customer.getPhone() != null && !customer.getPhone().isBlank()
                ? whatsAppLinkService.buildLink(customer.getPhone(),
                        order.getStatus() == ServiceOrderStatus.COMPLETED || order.getStatus() == ServiceOrderStatus.PICKED_UP
                                ? "Hi " + customer.getFullName() + ", your order #" + order.getOrderNumber() + " is ready for pickup!"
                                : "Hi " + customer.getFullName() + ", this is regarding your order #" + order.getOrderNumber() + ".")
                : null;

        return ServiceOrderResponse.from(
                order,
                type != null ? type.getName() : null,
                customer != null ? customer.getFullName() : null,
                catalogItem != null ? catalogItem.getName() : null,
                assignedStaffName,
                createdByName,
                bookingPaymentStatus,
                bookingWhatsappLink,
                customerWhatsappLink
        );
    }
}
