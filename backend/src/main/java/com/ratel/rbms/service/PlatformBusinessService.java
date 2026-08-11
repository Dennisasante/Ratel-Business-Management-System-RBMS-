package com.ratel.rbms.service;

import com.ratel.rbms.dto.AdminResetPasswordResponse;
import com.ratel.rbms.dto.PlatformBusinessBillingUpdateRequest;
import com.ratel.rbms.dto.PlatformBusinessDetailResponse;
import com.ratel.rbms.dto.PlatformBusinessSummaryResponse;
import com.ratel.rbms.dto.UserResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessIntegrations;
import com.ratel.rbms.entity.SubscriptionPlan;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.BusinessIntegrationsRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomWigRequestRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.EcommerceOrderRepository;
import com.ratel.rbms.repository.ExpenseRepository;
import com.ratel.rbms.repository.ProductRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.SubscriptionPlanRepository;
import com.ratel.rbms.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final BusinessIntegrationsRepository businessIntegrationsRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditLogService auditLogService;

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
            BusinessIntegrationsRepository businessIntegrationsRepository,
            PasswordEncoder passwordEncoder,
            PlatformAuditLogService auditLogService
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
        this.businessIntegrationsRepository = businessIntegrationsRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
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

        if (req.trialEndsAt() != null) {
            business.setTrialEndsAt(req.trialEndsAt());
        }

        if (req.clearPriceOverride()) {
            business.setPriceOverride(null);
        } else if (req.priceOverride() != null) {
            business.setPriceOverride(req.priceOverride());
        }

        business = businessRepository.save(business);

        auditLogService.log(adminId, "Updated billing for \"" + business.getName() + "\"",
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
