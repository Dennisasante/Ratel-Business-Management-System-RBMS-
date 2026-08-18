package com.ratel.rbms.service;

import com.ratel.rbms.dto.MobileMoneyChargeRequest;
import com.ratel.rbms.dto.MobileMoneyChargeResponse;
import com.ratel.rbms.dto.RecordPaymentRequest;
import com.ratel.rbms.dto.RefundRequest;
import com.ratel.rbms.dto.ServiceOrderItemRequest;
import com.ratel.rbms.dto.ServiceOrderItemResponse;
import com.ratel.rbms.dto.ServiceOrderRequest;
import com.ratel.rbms.dto.ServiceOrderResponse;
import com.ratel.rbms.dto.ServiceOrderUpdateRequest;
import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessIntegrations;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.ServiceOrderItem;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.entity.PendingApproval;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.StaffMember;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.PaymentMethod;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.exception.ApprovalRequiredException;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.BusinessIntegrationsRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.ServiceOrderItemRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.StaffMemberRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ServiceOrderService {

    private static final int PAGE_SIZE = 50;

    // The fixed line is RECEIVED -> COMPLETED -> PICKED_UP; CANCELLED is a branch off any
    // non-terminal status. Each forward edge also has its one-step-back reverse
    // (COMPLETED->RECEIVED, PICKED_UP->COMPLETED) plus CANCELLED->RECEIVED as a "reopen" —
    // so a mistaken status change is always correctable without allowing an arbitrary jump
    // (e.g. RECEIVED straight to PICKED_UP), which would make the pipeline meaningless.
    // Every change here (forward or back) is already logged unconditionally below, so a
    // correction is automatically an audit trail entry.
    //
    // IN_PROGRESS was dropped from the pipeline (the enum constant itself is kept, not
    // removed, so any historical row still sitting in that status keeps deserializing —
    // there's no migration backfilling it away). It's kept here as a legacy exit-only node:
    // nothing transitions into it anymore, but an order already there can still move on.
    private static final Map<ServiceOrderStatus, Set<ServiceOrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            ServiceOrderStatus.RECEIVED, EnumSet.of(ServiceOrderStatus.COMPLETED, ServiceOrderStatus.CANCELLED),
            ServiceOrderStatus.IN_PROGRESS, EnumSet.of(ServiceOrderStatus.RECEIVED, ServiceOrderStatus.COMPLETED, ServiceOrderStatus.CANCELLED),
            ServiceOrderStatus.COMPLETED, EnumSet.of(ServiceOrderStatus.RECEIVED, ServiceOrderStatus.PICKED_UP, ServiceOrderStatus.CANCELLED),
            ServiceOrderStatus.PICKED_UP, EnumSet.of(ServiceOrderStatus.COMPLETED),
            ServiceOrderStatus.CANCELLED, EnumSet.of(ServiceOrderStatus.RECEIVED)
    );

    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceOrderItemRepository serviceOrderItemRepository;
    private final UserRepository userRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final BusinessRepository businessRepository;
    private final CustomerService customerService;
    private final ServiceCatalogService serviceCatalogService;
    private final ServiceTypeService serviceTypeService;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;
    private final BookingRepository bookingRepository;
    private final WhatsAppLinkService whatsAppLinkService;
    private final BusinessIntegrationsRepository businessIntegrationsRepository;
    private final PaystackService paystackService;
    private final PaymentTransactionService paymentTransactionService;
    private final ApprovalGateService approvalGateService;

    public ServiceOrderService(
            ServiceOrderRepository serviceOrderRepository,
            ServiceOrderItemRepository serviceOrderItemRepository,
            UserRepository userRepository,
            StaffMemberRepository staffMemberRepository,
            BusinessRepository businessRepository,
            CustomerService customerService,
            ServiceCatalogService serviceCatalogService,
            ServiceTypeService serviceTypeService,
            EmailService emailService,
            ActivityLogService activityLogService,
            BookingRepository bookingRepository,
            WhatsAppLinkService whatsAppLinkService,
            BusinessIntegrationsRepository businessIntegrationsRepository,
            PaystackService paystackService,
            PaymentTransactionService paymentTransactionService,
            ApprovalGateService approvalGateService
    ) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.serviceOrderItemRepository = serviceOrderItemRepository;
        this.userRepository = userRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.businessRepository = businessRepository;
        this.customerService = customerService;
        this.serviceCatalogService = serviceCatalogService;
        this.serviceTypeService = serviceTypeService;
        this.emailService = emailService;
        this.activityLogService = activityLogService;
        this.bookingRepository = bookingRepository;
        this.whatsAppLinkService = whatsAppLinkService;
        this.businessIntegrationsRepository = businessIntegrationsRepository;
        this.paystackService = paystackService;
        this.paymentTransactionService = paymentTransactionService;
        this.approvalGateService = approvalGateService;
    }

    // Every service on the order, resolved and validated up front — same
    // per-item checks a single-service order always got (type/catalog item
    // must belong to this business), just once per line now.
    private record ResolvedItem(ServiceType type, ServiceCatalogItem catalogItem, String name, BigDecimal price, BigDecimal discountAmount) {
    }

    @Transactional
    public ServiceOrderResponse create(ServiceOrderRequest req) {
        UUID businessId = TenantContext.getBusinessId();

        if (req.customerId() != null) {
            customerService.getOwned(req.customerId()); // validates ownership; response reloads it via toResponse()
        }

        List<ResolvedItem> resolvedItems = new ArrayList<>();
        for (ServiceOrderItemRequest itemReq : req.items()) {
            ServiceType itemType = serviceTypeService.getOwned(itemReq.serviceTypeId());
            ServiceCatalogItem catalogItem = null;
            String name = itemType.getName();
            if (itemReq.serviceCatalogId() != null) {
                catalogItem = serviceCatalogService.getOwned(itemReq.serviceCatalogId());
                name = catalogItem.getName();
            }
            // A typed description (e.g. from an Instagram/WhatsApp order) always
            // wins over the catalog/category name — it's more specific and is
            // exactly what the person recording the order chose to say.
            if (itemReq.customName() != null && !itemReq.customName().isBlank()) {
                name = itemReq.customName().trim();
            }
            BigDecimal itemDiscount = itemReq.discountAmount() != null ? itemReq.discountAmount() : BigDecimal.ZERO;
            resolvedItems.add(new ResolvedItem(itemType, catalogItem, name, itemReq.price(), itemDiscount));
        }

        BigDecimal totalPrice = resolvedItems.stream().map(ResolvedItem::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDiscount = resolvedItems.stream().map(ResolvedItem::discountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        ResolvedItem first = resolvedItems.get(0);

        ServiceOrder order = ServiceOrder.builder()
                .businessId(businessId)
                .serviceTypeId(first.type().getId())
                // Keeps a single-service order's response byte-for-byte identical
                // to before (serviceCatalogId only meaningful when there's exactly
                // one item — otherwise the items list below is the source of truth).
                .serviceCatalogId(resolvedItems.size() == 1 && first.catalogItem() != null ? first.catalogItem().getId() : null)
                .status(ServiceOrderStatus.RECEIVED)
                .customerId(req.customerId())
                .notes(req.notes())
                .price(totalPrice)
                .discountAmount(totalDiscount)
                .assignedStaffId(req.assignedStaffId())
                .receivedAt(Instant.now())
                .scheduledAt(req.scheduledAt())
                .createdBy(TenantContext.getUserId())
                .build();
        order = serviceOrderRepository.save(order);
        serviceOrderRepository.flush(); // so order_number is readable below

        for (ResolvedItem ri : resolvedItems) {
            serviceOrderItemRepository.save(ServiceOrderItem.builder()
                    .businessId(businessId)
                    .serviceOrderId(order.getId())
                    .serviceTypeId(ri.type().getId())
                    .serviceCatalogId(ri.catalogItem() != null ? ri.catalogItem().getId() : null)
                    .serviceName(ri.name())
                    .price(ri.price())
                    .discountAmount(ri.discountAmount())
                    .build());
        }

        String serviceSummary = resolvedItems.size() == 1 ? first.name() : resolvedItems.size() + " services";
        activityLogService.log(
                "Created service order #" + order.getOrderNumber() + " (" + serviceSummary + ") for GH₵" + totalPrice,
                "SERVICE_ORDER", order.getId()
        );

        return toResponse(order);
    }

    public List<ServiceOrderResponse> list(UUID serviceTypeId, ServiceOrderStatus status, int page) {
        UUID businessId = TenantContext.getBusinessId();
        List<ServiceOrder> orders = serviceOrderRepository.search(
                businessId, serviceTypeId, status, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        return orders.stream().map(this::toResponse).toList();
    }

    public ServiceOrderResponse get(UUID id) {
        return toResponse(getOwned(id));
    }

    @Transactional
    public ServiceOrderResponse update(UUID id, ServiceOrderUpdateRequest req) {
        ServiceOrder order = getOwned(id);

        if (req.price() != null) {
            // Validate up front — for everyone, so an invalid request never
            // gets queued for approval either.
            List<ServiceOrderItem> items = serviceOrderItemRepository.findAllByServiceOrderId(order.getId());
            if (items.size() > 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "This order has more than one service — editing individual services isn't supported yet.");
            }
            if (!approvalGateService.isOwner()) {
                // A price change bundled with other field changes in the same
                // PATCH goes to approval as one atomic request — notes/assignee/
                // scheduling don't apply piecemeal ahead of the price.
                UUID pendingId = approvalGateService.queueForApproval(
                        PendingApproval.SourceType.SERVICE_ORDER, PendingApproval.ActionType.EDIT_PRICE, id, req,
                        "Edit price on service order #" + order.getOrderNumber() + ": GH₵" + order.getPrice() + " → GH₵" + req.price());
                throw new ApprovalRequiredException(pendingId, "Price change submitted for Owner approval.");
            }
        }

        return doUpdate(order, req);
    }

    @Transactional
    public ServiceOrderResponse applyApprovedUpdate(UUID id, ServiceOrderUpdateRequest req) {
        return doUpdate(getOwned(id), req);
    }

    private ServiceOrderResponse doUpdate(ServiceOrder order, ServiceOrderUpdateRequest req) {
        if (req.price() != null) {
            List<ServiceOrderItem> items = serviceOrderItemRepository.findAllByServiceOrderId(order.getId());
            BigDecimal discountAmount = req.discountAmount() != null ? req.discountAmount() : BigDecimal.ZERO;
            order.setPrice(req.price());
            order.setDiscountAmount(discountAmount);
            if (items.size() == 1) {
                // Keep the single line item in sync with the order-level total —
                // they're the same number while there's only one service.
                ServiceOrderItem item = items.get(0);
                item.setPrice(req.price());
                item.setDiscountAmount(discountAmount);
                serviceOrderItemRepository.save(item);
            }
        }

        order.setNotes(req.notes());
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

    // Charges the customer's mobile money wallet directly by phone number — the
    // "input the customer's number, they get a prompt on their phone" flow, for
    // whoever's behind the till rather than the customer typing card details
    // themselves. The order's paymentStatus stays UNPAID until a later
    // verifyPayment(reference) confirms the customer actually approved it.
    public MobileMoneyChargeResponse chargeMobileMoney(UUID id, MobileMoneyChargeRequest req) {
        ServiceOrder order = getOwned(id);
        if ("PAID".equals(order.getPaymentStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This order is already paid.");
        }
        Customer customer = customerService.getOrNull(order.getCustomerId());
        String customerEmail = resolveCustomerEmail(customer, order.getBusinessId(), order.getId());

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(order.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment yet."));
        if (integrations.getPaystackSecretKey() == null || integrations.getPaystackSecretKey().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment yet.");
        }

        long amountMinorUnits = order.getPrice().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        String reference = "SVCORDER-MOMO-" + order.getBusinessId().toString().substring(0, 8) + "-" + UUID.randomUUID().toString().substring(0, 8);

        PaystackService.MobileMoneyChargeResult result = paystackService.chargeMobileMoney(
                integrations.getPaystackSecretKey(), customerEmail, amountMinorUnits, req.phone(), req.provider(), reference
        );

        order.setPaystackReference(result.reference());
        if (result.success()) {
            order.setPaymentStatus("PAID");
            order.setAmountPaid(order.getPrice());
            activityLogService.log("Charged mobile money for service order #" + order.getOrderNumber(), "SERVICE_ORDER", order.getId());
        }
        serviceOrderRepository.save(order);

        // An outright rejection (Paystack never even started a charge attempt —
        // see chargeMobileMoney()'s javadoc) is logged as FAILED, not PENDING —
        // there's no reference to later verify against, so it would otherwise
        // sit "pending" in the ledger forever.
        paymentTransactionService.record(
                order.getBusinessId(), PaymentTransaction.Direction.INCOMING, PaymentTransaction.SourceType.SERVICE_ORDER,
                order.getId(), "PAYSTACK", "MOBILE_MONEY", order.getPrice(),
                result.success() ? "SUCCESS" : ("failed".equalsIgnoreCase(result.status()) ? "FAILED" : "PENDING"),
                result.reference(), order.getCustomerId(), req.phone(), null, TenantContext.getUserId()
        );

        return new MobileMoneyChargeResponse(result.reference(), result.status(), result.displayText(), result.message());
    }

    // Completes a mobile-money charge that came back "send_otp" — the customer
    // reads back the code Paystack texted them, staff enters it here. A wrong/
    // expired code just comes back non-success (order stays UNPAID, try again),
    // not an error.
    @Transactional
    public ServiceOrderResponse submitMobileMoneyOtp(String reference, String otp) {
        ServiceOrder order = serviceOrderRepository.findByPaystackReferenceAndBusinessId(reference, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown payment reference."));

        if ("PAID".equals(order.getPaymentStatus())) {
            return toResponse(order);
        }

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(order.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment."));

        PaystackService.ChargeResult result = paystackService.submitOtp(integrations.getPaystackSecretKey(), otp, reference);

        if (result.success()) {
            order.setPaymentStatus("PAID");
            order.setAmountPaid(order.getPrice());
            order = serviceOrderRepository.save(order);
            activityLogService.log("Charged mobile money for service order #" + order.getOrderNumber(), "SERVICE_ORDER", order.getId());
            paymentTransactionService.updateStatusByReference(order.getBusinessId(), reference, "SUCCESS");
            return toResponse(order);
        }

        if ("failed".equalsIgnoreCase(result.status())) {
            order.setPaymentStatus("FAILED");
            order = serviceOrderRepository.save(order);
            paymentTransactionService.updateStatusByReference(order.getBusinessId(), reference, "FAILED");
        }

        // Anything short of success — "failed" isn't the only shape Paystack
        // sends back here. A status like "pending" means the customer needs to
        // approve via their mobile money app's own Approvals tab instead of
        // reading back an OTP — result.message() is Paystack's real
        // explanation of that, and is worth showing instead of a generic
        // "that code didn't work" that gives the cashier nothing to act on.
        // Only the literal "failed" case above marks the order FAILED, so a
        // genuinely still-pending charge stays resolvable via verifyPayment().
        throw new ApiException(HttpStatus.BAD_REQUEST,
                result.message() != null && !result.message().isBlank()
                        ? result.message()
                        : "That code didn't work. If the customer got an approval prompt in their mobile money app instead of a code, use Verify once they've approved it there.");
    }

    @Transactional
    public ServiceOrderResponse verifyPayment(String reference) {
        ServiceOrder order = serviceOrderRepository.findByPaystackReferenceAndBusinessId(reference, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown payment reference."));

        if ("PAID".equals(order.getPaymentStatus())) {
            return toResponse(order);
        }

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(order.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment."));

        PaystackService.VerifyResult verify = paystackService.verifyTransaction(integrations.getPaystackSecretKey(), reference);

        order.setPaymentStatus(verify.success() ? "PAID" : "FAILED");
        if (verify.success()) {
            order.setAmountPaid(order.getPrice());
        }
        order = serviceOrderRepository.save(order);

        if (verify.success()) {
            activityLogService.log("Confirmed payment for service order #" + order.getOrderNumber(), "SERVICE_ORDER", order.getId());
        }

        paymentTransactionService.record(
                order.getBusinessId(), PaymentTransaction.Direction.INCOMING, PaymentTransaction.SourceType.SERVICE_ORDER,
                order.getId(), "PAYSTACK", "MOBILE_MONEY", order.getPrice(), verify.success() ? "SUCCESS" : "FAILED",
                reference, order.getCustomerId(), null, null, TenantContext.getUserId()
        );

        return toResponse(order);
    }

    // A plain manual action (no Paystack) for cash/other payment — most walk-in
    // customers won't be paying by card through the dashboard, so this stays
    // available regardless of whether the business has Paystack configured.
    // Thin wrapper over recordPayment(): "mark paid" just means "the whole
    // remaining balance came in, in cash."
    @Transactional
    public ServiceOrderResponse markPaid(UUID id) {
        ServiceOrder order = getOwned(id);
        BigDecimal balanceDue = order.getPrice().subtract(order.getAmountPaid()).max(BigDecimal.ZERO);
        return recordPayment(id, new RecordPaymentRequest(balanceDue, PaymentMethod.CASH, "Marked paid manually"));
    }

    // Records a payment against an order's outstanding balance — the manual
    // "type in what came in" counterpart to the gateway flows above, and the
    // only path that can produce PARTIALLY_PAID.
    @Transactional
    public ServiceOrderResponse recordPayment(UUID id, RecordPaymentRequest req) {
        ServiceOrder order = getOwned(id);
        BigDecimal balanceDue = order.getPrice().subtract(order.getAmountPaid()).max(BigDecimal.ZERO);
        if (balanceDue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This order is already fully paid.");
        }
        if (req.amount().compareTo(balanceDue) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "That's more than the outstanding balance of GH₵" + balanceDue + ".");
        }

        BigDecimal newAmountPaid = order.getAmountPaid().add(req.amount());
        order.setAmountPaid(newAmountPaid);
        order.setPaymentStatus(derivePaymentStatus(newAmountPaid, order.getPrice()));
        order = serviceOrderRepository.save(order);

        activityLogService.log(
                "Recorded a GH₵" + req.amount() + " payment on service order #" + order.getOrderNumber(), "SERVICE_ORDER", order.getId());

        paymentTransactionService.record(
                order.getBusinessId(), PaymentTransaction.Direction.INCOMING, PaymentTransaction.SourceType.SERVICE_ORDER,
                order.getId(), "MANUAL", req.method().name(), req.amount(), "SUCCESS",
                null, order.getCustomerId(), null, req.note(), TenantContext.getUserId()
        );

        return toResponse(order);
    }

    // Full refund only — no partial-refund reconciliation. amountPaid is left
    // as-is (a record of what was actually collected); REFUNDED overrides
    // paymentStatus so the ledger's OUTGOING row is the source of truth for
    // the refund event itself.
    @Transactional
    public ServiceOrderResponse refund(UUID id, RefundRequest req) {
        ServiceOrder order = getOwned(id);
        if (order.getAmountPaid().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing has been collected on this order to refund.");
        }
        if (!approvalGateService.isOwner()) {
            UUID pendingId = approvalGateService.queueForApproval(
                    PendingApproval.SourceType.SERVICE_ORDER, PendingApproval.ActionType.REFUND, id, req,
                    "Refund GH₵" + order.getAmountPaid() + " on service order #" + order.getOrderNumber());
            throw new ApprovalRequiredException(pendingId, "Refund submitted for Owner approval.");
        }
        return doRefund(order, req);
    }

    // Called ONLY by PendingApprovalService.approve() — the Owner clicking
    // Approve IS the authorization, so this bypasses the gate entirely.
    @Transactional
    public ServiceOrderResponse applyApprovedRefund(UUID id, RefundRequest req) {
        return doRefund(getOwned(id), req);
    }

    private ServiceOrderResponse doRefund(ServiceOrder order, RefundRequest req) {
        BigDecimal refundedAmount = order.getAmountPaid();
        order.setPaymentStatus("REFUNDED");
        order = serviceOrderRepository.save(order);

        activityLogService.log("Refunded GH₵" + refundedAmount + " on service order #" + order.getOrderNumber(), "SERVICE_ORDER", order.getId());

        paymentTransactionService.record(
                order.getBusinessId(), PaymentTransaction.Direction.OUTGOING, PaymentTransaction.SourceType.SERVICE_ORDER,
                order.getId(), "MANUAL", null, refundedAmount, "SUCCESS",
                null, order.getCustomerId(), null, req.note() != null && !req.note().isBlank() ? req.note() : "Refunded",
                TenantContext.getUserId()
        );

        return toResponse(order);
    }

    private static String derivePaymentStatus(BigDecimal amountPaid, BigDecimal total) {
        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) return "UNPAID";
        if (amountPaid.compareTo(total) >= 0) return "PAID";
        return "PARTIALLY_PAID";
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

    // Paystack's charge/checkout API requires a customer email even though
    // walk-ins are routinely paid by phone number alone — this synthesizes a
    // stable, invisible-to-the-customer placeholder so online payment never
    // blocks on "add an email first." A real email on file always wins.
    private String resolveCustomerEmail(Customer customer, UUID businessId, UUID orderId) {
        if (customer != null && customer.getEmail() != null && !customer.getEmail().isBlank()) {
            return customer.getEmail();
        }
        Business business = businessRepository.findById(businessId).orElse(null);
        String slug = business != null && business.getSlug() != null && !business.getSlug().isBlank()
                ? business.getSlug()
                : businessId.toString().substring(0, 8);
        return "order-" + orderId.toString().substring(0, 8) + "@" + slug + ".ratelsystems.tech";
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
        List<ServiceOrderItem> items = serviceOrderItemRepository.findAllByServiceOrderId(order.getId());

        // Cache type-name lookups across items — a multi-item order commonly
        // repeats the same one or two categories, no need to re-query per line.
        Map<UUID, String> typeNameCache = new HashMap<>();
        if (type != null) typeNameCache.put(type.getId(), type.getName());
        List<ServiceOrderItemResponse> itemResponses = items.stream()
                .map(item -> ServiceOrderItemResponse.from(item, typeNameCache.computeIfAbsent(item.getServiceTypeId(), this::typeNameOrNull)))
                .toList();

        // StaffMember first — that's what assignedStaffId points at now. Falls
        // back to User only for a pre-migration edge case; the V34 migration
        // guarantees no id collisions between the two tables, so this is just
        // a safety net, not the expected path.
        String assignedStaffName = order.getAssignedStaffId() != null
                ? staffMemberRepository.findById(order.getAssignedStaffId()).map(StaffMember::getFullName)
                        .orElseGet(() -> userRepository.findById(order.getAssignedStaffId()).map(User::getFullName).orElse(null))
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
                itemResponses,
                assignedStaffName,
                createdByName,
                bookingPaymentStatus,
                bookingWhatsappLink,
                customerWhatsappLink
        );
    }

    private ServiceType findTypeOrNull(UUID id) {
        try {
            return serviceTypeService.getOwned(id);
        } catch (ApiException e) {
            return null; // service type may have been removed since the order was created
        }
    }

    private String typeNameOrNull(UUID id) {
        ServiceType type = findTypeOrNull(id);
        return type != null ? type.getName() : null;
    }

    private ServiceCatalogItem findCatalogItemOrNull(UUID id) {
        try {
            return serviceCatalogService.getOwned(id);
        } catch (ApiException e) {
            return null; // catalog item may have been removed since the order was created
        }
    }
}
