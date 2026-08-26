package com.ratel.rbms.service;

import com.ratel.rbms.dto.AdminResetPasswordResponse;
import com.ratel.rbms.dto.CustomWigRequestDetailResponse;
import com.ratel.rbms.dto.CustomWigRequestResponse;
import com.ratel.rbms.dto.ExpenseResponse;
import com.ratel.rbms.dto.PaymentTransactionResponse;
import com.ratel.rbms.dto.PlatformBusinessBillingUpdateRequest;
import com.ratel.rbms.dto.PlatformBusinessDetailResponse;
import com.ratel.rbms.dto.PlatformBusinessModulesUpdateRequest;
import com.ratel.rbms.dto.PlatformBusinessSummaryResponse;
import com.ratel.rbms.dto.PlatformCustomerSummaryResponse;
import com.ratel.rbms.dto.PlatformSaleSummaryResponse;
import com.ratel.rbms.dto.PlatformServiceOrderSummaryResponse;
import com.ratel.rbms.dto.SaleResponse;
import com.ratel.rbms.dto.ServiceOrderResponse;
import com.ratel.rbms.dto.SubscriptionPaymentResponse;
import com.ratel.rbms.dto.UserResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessIntegrations;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.entity.Product;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.SaleItem;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.StockMovement;
import com.ratel.rbms.entity.SubscriptionPlan;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.BillingStatus;
import com.ratel.rbms.entity.enums.MovementType;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.BusinessIntegrationsRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomWigRequestRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.EcommerceOrderRepository;
import com.ratel.rbms.repository.ExpenseRepository;
import com.ratel.rbms.repository.PaymentTransactionRepository;
import com.ratel.rbms.repository.ProductRepository;
import com.ratel.rbms.repository.SaleItemRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.StockMovementRepository;
import com.ratel.rbms.repository.SubscriptionPaymentRepository;
import com.ratel.rbms.repository.SubscriptionPlanRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PlatformBusinessService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final ExpenseRepository expenseRepository;
    private final BookingRepository bookingRepository;
    private final EcommerceOrderRepository ecommerceOrderRepository;
    private final CustomWigRequestRepository customWigRequestRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final BusinessIntegrationsRepository businessIntegrationsRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditLogService auditLogService;
    private final PaymentTransactionService paymentTransactionService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SaleItemRepository saleItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ActivityLogService activityLogService;
    private final SaleService saleService;
    private final ServiceOrderService serviceOrderService;
    private final BookingService bookingService;
    private final CustomWigRequestService customWigRequestService;

    public PlatformBusinessService(
            BusinessRepository businessRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            CustomerRepository customerRepository,
            SaleRepository saleRepository,
            ExpenseRepository expenseRepository,
            BookingRepository bookingRepository,
            EcommerceOrderRepository ecommerceOrderRepository,
            CustomWigRequestRepository customWigRequestRepository,
            ServiceOrderRepository serviceOrderRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            SubscriptionPaymentRepository subscriptionPaymentRepository,
            BusinessIntegrationsRepository businessIntegrationsRepository,
            PasswordEncoder passwordEncoder,
            PlatformAuditLogService auditLogService,
            PaymentTransactionService paymentTransactionService,
            PaymentTransactionRepository paymentTransactionRepository,
            SaleItemRepository saleItemRepository,
            StockMovementRepository stockMovementRepository,
            ActivityLogService activityLogService,
            SaleService saleService,
            ServiceOrderService serviceOrderService,
            BookingService bookingService,
            CustomWigRequestService customWigRequestService
    ) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.expenseRepository = expenseRepository;
        this.bookingRepository = bookingRepository;
        this.ecommerceOrderRepository = ecommerceOrderRepository;
        this.customWigRequestRepository = customWigRequestRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.subscriptionPaymentRepository = subscriptionPaymentRepository;
        this.businessIntegrationsRepository = businessIntegrationsRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.paymentTransactionService = paymentTransactionService;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.saleItemRepository = saleItemRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.activityLogService = activityLogService;
        this.saleService = saleService;
        this.serviceOrderService = serviceOrderService;
        this.bookingService = bookingService;
        this.customWigRequestService = customWigRequestService;
    }

    // Full payment-event visibility for a single business — "so I'm never
    // found wanting" when the owner needs to verify a payment dispute or
    // check what actually moved through the system.
    public List<PaymentTransactionResponse> getPaymentTransactions(UUID businessId, Instant from, Instant to) {
        businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        return paymentTransactionService.search(businessId, null, null, from, to);
    }

    // What this business has paid TALLIA for its own subscription — a
    // separate concern from getPaymentTransactions() above (that's the money
    // this business collected from ITS OWN customers). Previously invisible
    // to Super Admin entirely: the owner's own Billing page already showed
    // this via BillingService.history(), but nothing surfaced it here.
    public List<SubscriptionPaymentResponse> getSubscriptionPayments(UUID businessId) {
        businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        return subscriptionPaymentRepository.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(p -> SubscriptionPaymentResponse.from(p, planNameOrNull(p.getSubscriptionPlanId())))
                .toList();
    }

    private String planNameOrNull(UUID planId) {
        return subscriptionPlanRepository.findById(planId).map(SubscriptionPlan::getName).orElse(null);
    }

    // Cross-checks a stuck/PENDING gateway transaction — the same mechanism as
    // the business owner's own re-verify button on PaymentTransactionController,
    // reused (not duplicated) here so Super Admin can confirm a payment during a
    // support call without needing the owner to do it themselves.
    @Transactional
    public void verifyPaymentTransaction(UUID adminId, UUID businessId, UUID transactionId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));

        PaymentTransaction t = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction not found."));
        if (!t.getBusinessId().equals(businessId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Transaction not found.");
        }
        if (!"PAYSTACK".equals(t.getGateway()) || t.getGatewayReference() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing to verify — this wasn't a gateway payment.");
        }

        // Each flow's verifyPayment() reads TenantContext.getBusinessId() — normally
        // stamped by JwtAuthenticationFilter from the caller's own JWT, but a Super
        // Admin's platform token carries no business_id at all (see that filter's
        // own comment). Set it here for this call so those methods resolve the
        // right tenant instead of throwing "No business_id in tenant context."
        TenantContext.setBusinessId(businessId);
        switch (t.getSourceType()) {
            case SALE -> saleService.verifyPayment(t.getGatewayReference());
            case SERVICE_ORDER -> serviceOrderService.verifyPayment(t.getGatewayReference());
            case BOOKING -> bookingService.verifyPayment(t.getGatewayReference());
            case PURCHASE_ORDER -> throw new ApiException(HttpStatus.BAD_REQUEST, "Purchase orders are paid manually — there's nothing to verify.");
            case CUSTOM_WIG_REQUEST -> customWigRequestService.verifyPayment(t.getGatewayReference());
        }

        auditLogService.log(adminId, "Verified a payment for \"" + business.getName() + "\"", businessId, business.getName(), null);
    }

    // Read-only listings backing the Super Admin data-cleanup panel — "remove
    // whatever test stuff a staff member created on a real account."
    public List<PlatformServiceOrderSummaryResponse> listServiceOrders(UUID businessId) {
        businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        List<ServiceOrder> orders = serviceOrderRepository.findAllByBusinessIdOrderByReceivedAtDesc(businessId);
        Map<UUID, String> customerNames = customerNamesFor(businessId);
        return orders.stream()
                .map(o -> new PlatformServiceOrderSummaryResponse(
                        o.getId(), o.getOrderNumber(),
                        o.getCustomerId() != null ? customerNames.getOrDefault(o.getCustomerId(), "Unknown") : "Walk-in",
                        o.getPrice(), o.getStatus().name(), o.getPaymentStatus(), o.getReceivedAt()
                ))
                .toList();
    }

    public List<PlatformSaleSummaryResponse> listSales(UUID businessId) {
        businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        List<Sale> sales = saleRepository.findAllByBusinessIdOrderByCreatedAtDesc(businessId);
        Map<UUID, String> customerNames = customerNamesFor(businessId);
        return sales.stream()
                .map(s -> new PlatformSaleSummaryResponse(
                        s.getId(), s.getSaleNumber(),
                        s.getCustomerId() != null ? customerNames.getOrDefault(s.getCustomerId(), "Unknown") : "Walk-in",
                        s.getTotalAmount(), s.getPaymentStatus(), s.getCreatedAt()
                ))
                .toList();
    }

    public List<PlatformCustomerSummaryResponse> listCustomers(UUID businessId) {
        businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        return customerRepository.findAllByBusinessIdOrderByFullNameAsc(businessId).stream()
                .map(c -> new PlatformCustomerSummaryResponse(c.getId(), c.getFullName(), c.getPhone(), c.getEmail(), c.getCreatedAt()))
                .toList();
    }

    public List<ExpenseResponse> listExpenses(UUID businessId) {
        businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        Map<UUID, String> userNames = new HashMap<>();
        for (User u : userRepository.findAllByBusinessId(businessId)) {
            userNames.put(u.getId(), u.getFullName());
        }
        return expenseRepository.findAllByBusinessIdOrderByExpenseDateDesc(businessId).stream()
                .map(e -> ExpenseResponse.from(e, e.getRecordedBy() != null ? userNames.getOrDefault(e.getRecordedBy(), "Unknown") : "System"))
                .toList();
    }

    public List<CustomWigRequestResponse> listCustomWigRequests(UUID businessId) {
        businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        return customWigRequestService.listForPlatform(businessId);
    }

    // Full-detail single-record fetches for the Super Admin support view —
    // reuse the same domain-service response construction via the
    // TenantContext-stamping trick already used by verifyPaymentTransaction()
    // above, since SaleService/ServiceOrderService.get() read
    // TenantContext.getBusinessId() internally and a Super Admin's platform
    // token never carries one. CustomWigRequestService has its own
    // feature-gate-free variant instead (see getForPlatform()'s own comment).
    public SaleResponse getSaleDetail(UUID businessId, UUID saleId) {
        businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        TenantContext.setBusinessId(businessId);
        return saleService.get(saleId);
    }

    public ServiceOrderResponse getServiceOrderDetail(UUID businessId, UUID orderId) {
        businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        TenantContext.setBusinessId(businessId);
        return serviceOrderService.get(orderId);
    }

    public CustomWigRequestDetailResponse getCustomWigRequestDetail(UUID businessId, UUID id) {
        businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        return customWigRequestService.getForPlatform(businessId, id);
    }

    private Map<UUID, String> customerNamesFor(UUID businessId) {
        Map<UUID, String> names = new HashMap<>();
        for (Customer c : customerRepository.findAllByBusinessIdOrderByFullNameAsc(businessId)) {
            names.put(c.getId(), c.getFullName());
        }
        return names;
    }

    // Deletes a single test record a staff member created, for cleanup on a
    // real business's account. Each removal is logged twice: once to the
    // Super Admin's own platform audit trail, and once to the business's own
    // activity feed (as "System"/no user) so the owner sees it happened too.
    @Transactional
    public void deleteServiceOrder(UUID adminId, UUID businessId, UUID orderId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        ServiceOrder order = serviceOrderRepository.findByIdAndBusinessId(orderId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service order not found."));
        long orderNumber = order.getOrderNumber();

        // service_order_items, service_order_photos, and any originating booking
        // all cascade at the DB level via ON DELETE CASCADE — only the ledger
        // (deliberately FK-less, since it's polymorphic across 4 source types)
        // needs an explicit cleanup pass.
        paymentTransactionRepository.deleteBySourceTypeAndSourceId(PaymentTransaction.SourceType.SERVICE_ORDER, orderId);
        serviceOrderRepository.delete(order);

        activityLogService.log(businessId, null, "Removed service order #" + orderNumber + " (by Super Admin)", "SERVICE_ORDER", orderId);
        auditLogService.log(adminId, "Deleted service order #" + orderNumber + " at \"" + business.getName() + "\"",
                businessId, business.getName(), null);
    }

    @Transactional
    public void deleteSale(UUID adminId, UUID businessId, UUID saleId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        Sale sale = saleRepository.findByIdAndBusinessId(saleId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sale not found."));
        long saleNumber = sale.getSaleNumber();

        // Restore the stock this sale removed — same audit trail a manual
        // adjustment uses (ProductService.adjustStock isn't callable here since
        // it reads TenantContext, which this platform-scoped request never sets).
        for (SaleItem item : saleItemRepository.findAllBySaleId(saleId)) {
            if (item.getProductId() == null) continue;
            productRepository.findByIdAndBusinessId(item.getProductId(), businessId).ifPresent(product -> {
                int newQuantity = product.getQuantity() + item.getQuantity();
                product.setQuantity(newQuantity);
                productRepository.save(product);
                stockMovementRepository.save(StockMovement.builder()
                        .businessId(businessId)
                        .productId(product.getId())
                        .movementType(MovementType.ADD)
                        .quantityChange(item.getQuantity())
                        .resultingQuantity(newQuantity)
                        .note("Reversal: deleted sale #" + saleNumber + " (by Super Admin)")
                        .build());
            });
        }

        paymentTransactionRepository.deleteBySourceTypeAndSourceId(PaymentTransaction.SourceType.SALE, saleId);
        saleRepository.delete(sale); // sale_items cascade at the DB level

        activityLogService.log(businessId, null, "Removed sale #" + saleNumber + " (by Super Admin)", "SALE", saleId);
        auditLogService.log(adminId, "Deleted sale #" + saleNumber + " at \"" + business.getName() + "\"",
                businessId, business.getName(), null);
    }

    @Transactional
    public void deleteCustomer(UUID adminId, UUID businessId, UUID customerId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        Customer customer = customerRepository.findByIdAndBusinessId(customerId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found."));
        String name = customer.getFullName();

        // Every order/sale/booking referencing this customer keeps existing
        // (ON DELETE SET NULL) and just reads as "Walk-in" going forward.
        customerRepository.delete(customer);

        activityLogService.log(businessId, null, "Removed customer \"" + name + "\" (by Super Admin)", "CUSTOMER", customerId);
        auditLogService.log(adminId, "Deleted customer \"" + name + "\" at \"" + business.getName() + "\"",
                businessId, business.getName(), null);
    }

    public List<PlatformBusinessSummaryResponse> search(String query, Boolean activeOnly) {
        List<Business> businesses = (query == null || query.isBlank())
                ? businessRepository.findAll()
                : businessRepository.findByNameContainingIgnoreCase(query);

        return businesses.stream()
                .filter(b -> activeOnly == null || b.isActive() == activeOnly)
                .map(this::toSummary)
                .toList();
    }

    public PlatformBusinessDetailResponse getDetail(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));

        List<User> rawUsers = userRepository.findAllByBusinessId(businessId);
        List<UserResponse> users = rawUsers.stream().map(UserResponse::from).toList();

        int productCount = (int) productRepository.countByBusinessId(businessId);
        int customerCount = (int) customerRepository.countByBusinessId(businessId);
        List<com.ratel.rbms.entity.Sale> sales = saleRepository.findAllByBusinessIdOrderByCreatedAtDesc(businessId);
        List<com.ratel.rbms.entity.Expense> expenses = expenseRepository.findAllByBusinessIdOrderByExpenseDateDesc(businessId);

        BigDecimal totalRevenue = sales.stream().map(com.ratel.rbms.entity.Sale::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpenses = expenses.stream().map(com.ratel.rbms.entity.Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        String planName = business.getSubscriptionPlanId() != null
                ? subscriptionPlanRepository.findById(business.getSubscriptionPlanId())
                        .map(SubscriptionPlan::getName)
                        .orElse("Unknown plan")
                : "No plan";

        long bookingCount = bookingRepository.countByBusinessIdAndTest(businessId, false);
        long ecommerceOrderCount = ecommerceOrderRepository.countByBusinessId(businessId);
        long customWigRequestCount = customWigRequestRepository.countByBusinessIdAndTest(businessId, false);
        long serviceOrderCount = serviceOrderRepository.countByBusinessId(businessId);

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(businessId).orElse(null);
        boolean paystackConfigured = integrations != null && integrations.getPaystackSecretKey() != null;
        boolean woocommerceConfigured = integrations != null
                && integrations.getWoocommerceConsumerKey() != null
                && integrations.getWoocommerceConsumerSecret() != null;
        boolean whatsappConfigured = integrations != null
                && integrations.getWhatsappNotifyNumber() != null
                && !integrations.getWhatsappNotifyNumber().isBlank();

        Map<String, Long> staffByRole = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            staffByRole.put(role.name(), 0L);
        }
        for (User user : rawUsers) {
            staffByRole.merge(user.getRole().name(), 1L, Long::sum);
        }

        return new PlatformBusinessDetailResponse(
                business.getId(),
                business.getName(),
                business.getIndustry().name(),
                business.getLocation(),
                business.getContactEmail(),
                business.getContactPhone(),
                business.getCurrency(),
                business.getSubscriptionPlan(),
                business.getEnabledModules(),
                business.isActive(),
                business.getCreatedAt(),
                users,
                productCount,
                customerCount,
                sales.size(),
                totalRevenue,
                expenses.size(),
                totalExpenses,
                business.getBillingStatus().name(),
                business.getTrialEndsAt(),
                business.getCurrentPeriodEndsAt(),
                business.getSubscriptionPlanId(),
                planName,
                business.getPriceOverride(),
                bookingCount,
                ecommerceOrderCount,
                customWigRequestCount,
                serviceOrderCount,
                paystackConfigured,
                woocommerceConfigured,
                whatsappConfigured,
                staffByRole
        );
    }

    public PlatformBusinessDetailResponse updateBilling(UUID adminId, UUID businessId, PlatformBusinessBillingUpdateRequest req) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));

        if (req.clearPlan()) {
            business.setSubscriptionPlanId(null);
        } else if (req.subscriptionPlanId() != null) {
            SubscriptionPlan plan = subscriptionPlanRepository.findById(req.subscriptionPlanId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Plan not found."));
            business.setSubscriptionPlanId(plan.getId());
        }

        // Extending trialEndsAt into the future is meant to actually restore
        // access, not just change a stored date — without this, editing the
        // date on a business that's already lapsed to READ_ONLY/GRACE had no
        // real effect, since BillingExpiryService.flipExpiredTrials() only
        // ever re-checks businesses that are CURRENTLY TRIALING. Scoped
        // strictly to a suspended business and a genuinely future date, so
        // this never demotes an already-ACTIVE paying business back down.
        boolean restoredAccess = false;
        if (req.trialEndsAt() != null) {
            business.setTrialEndsAt(req.trialEndsAt());
            boolean extendingIntoFuture = req.trialEndsAt().isAfter(Instant.now());
            boolean currentlySuspended = business.getBillingStatus() == BillingStatus.READ_ONLY
                    || business.getBillingStatus() == BillingStatus.GRACE;
            if (extendingIntoFuture && currentlySuspended) {
                business.setBillingStatus(BillingStatus.TRIALING);
                business.setGracePeriodEndsAt(null);
                restoredAccess = true;
            }
        }

        if (req.clearPriceOverride()) {
            business.setPriceOverride(null);
        } else if (req.priceOverride() != null) {
            business.setPriceOverride(req.priceOverride());
        }

        business = businessRepository.save(business);

        auditLogService.log(adminId, "Updated billing for \"" + business.getName() + "\""
                        + (restoredAccess ? " (restored from suspended to trialing)" : ""),
                business.getId(), business.getName(), null);

        return getDetail(business.getId());
    }

    // The permanent core set — every business gets these regardless of what
    // the Super Admin picks, so a mistaken save can never brick core
    // functionality. Kept out of the togglable list entirely (not just
    // pre-checked) on the Super Admin UI.
    private static final Set<String> CORE_MODULES = Set.of("INVENTORY", "SALES", "CUSTOMERS", "EXPENSES");

    private static final Set<String> TOGGLEABLE_MODULES = Set.of(
            "SERVICE_ORDERS", "CUSTOM_WIG_REQUESTS", "ECOMMERCE", "BOOKINGS", "SUPPLIERS_AND_PURCHASING", "AI"
    );

    public PlatformBusinessDetailResponse updateEnabledModules(UUID adminId, UUID businessId, PlatformBusinessModulesUpdateRequest req) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));

        for (String code : req.enabledModules()) {
            if (!TOGGLEABLE_MODULES.contains(code) && !CORE_MODULES.contains(code)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unrecognized module: " + code);
            }
        }

        Set<String> merged = new LinkedHashSet<>(CORE_MODULES);
        merged.addAll(req.enabledModules().stream().filter(TOGGLEABLE_MODULES::contains).toList());
        business.setEnabledModules(new ArrayList<>(merged));
        business = businessRepository.save(business);

        auditLogService.log(adminId, "Updated enabled modules for \"" + business.getName() + "\"",
                business.getId(), business.getName(), null);

        return getDetail(business.getId());
    }

    public PlatformBusinessSummaryResponse setActive(UUID adminId, UUID businessId, boolean active) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));

        business.setActive(active);
        business = businessRepository.save(business);

        auditLogService.log(adminId, (active ? "Reactivated" : "Suspended") + " business \"" + business.getName() + "\"",
                business.getId(), business.getName(), null);

        return toSummary(business);
    }

    public void deleteBusiness(UUID adminId, UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));

        int userCount = userRepository.findAllByBusinessId(businessId).size();
        String name = business.getName();

        // Cascades to users, products, sales, customers, expenses, and this
        // business's own activity_logs via ON DELETE CASCADE. The audit log
        // entry below is written to a table that deliberately has no such FK,
        // so the record that this business ever existed (and was deleted)
        // survives the deletion itself.
        businessRepository.delete(business);

        auditLogService.log(adminId, "Deleted business \"" + name + "\" (had " + userCount + " user"
                + (userCount == 1 ? "" : "s") + ")", businessId, name, null);
    }

    public AdminResetPasswordResponse resetUserPassword(UUID adminId, UUID businessId, UUID userId) {
        User user = userRepository.findByIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));

        String tempPassword = generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);

        auditLogService.log(adminId, "Reset password for \"" + user.getFullName() + "\" at \"" + business.getName() + "\"",
                businessId, business.getName(), user.getId());

        return new AdminResetPasswordResponse(tempPassword);
    }

    private PlatformBusinessSummaryResponse toSummary(Business business) {
        List<User> users = userRepository.findAllByBusinessId(business.getId());
        String ownerEmail = userRepository.findFirstByBusinessIdAndRole(business.getId(), Role.OWNER)
                .map(User::getEmail)
                .orElse("—");
        return new PlatformBusinessSummaryResponse(
                business.getId(),
                business.getName(),
                business.getIndustry().name(),
                business.getLocation(),
                business.getSubscriptionPlan(),
                business.isActive(),
                users.size(),
                ownerEmail,
                business.getCreatedAt(),
                business.getBillingStatus().name()
        );
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
