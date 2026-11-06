package com.ratel.rbms.service;

import com.ratel.rbms.dto.LoyaltyCustomerRowResponse;
import com.ratel.rbms.dto.LoyaltyProgramRequest;
import com.ratel.rbms.dto.LoyaltyProgramResponse;
import com.ratel.rbms.dto.LoyaltyStatusResponse;
import com.ratel.rbms.dto.PublicLoyaltyResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.LoyaltyCustomer;
import com.ratel.rbms.entity.LoyaltyProgram;
import com.ratel.rbms.entity.LoyaltyRedemption;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.SaleItem;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.enums.SaleItemType;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.LoyaltyCustomerRepository;
import com.ratel.rbms.repository.LoyaltyProgramRepository;
import com.ratel.rbms.repository.LoyaltyRedemptionRepository;
import com.ratel.rbms.repository.SaleItemRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loyalty points: a point is a visit. A customer earns at most one per day, when a sale that
 * includes a counting service is paid, or a service order is completed. Reaching the target
 * makes them due a reward, which staff apply and mark redeemed.
 *
 * Every number that matters is changed by one atomic statement (see LoyaltyCustomerRepository):
 * the one-visit-a-day rule is a database constraint, and a reward can only be redeemed if the
 * visits are really there — so racing sales or double-clicks can never inflate a balance.
 */
@Service
public class LoyaltyService {

    static final String MODULE = "LOYALTY";
    static final String SOURCE_SALE = "SALE";
    static final String SOURCE_SERVICE_ORDER = "SERVICE_ORDER";

    // Ghana is on GMT all year, but the zone is named so "today" never depends on the server's clock zone.
    private static final ZoneId VISIT_ZONE = ZoneId.of("Africa/Accra");

    // No look-alikes (i, l, o, 0, 1): these links get read off a phone screen.
    private static final String TOKEN_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int TOKEN_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LoyaltyProgramRepository programRepository;
    private final LoyaltyCustomerRepository loyaltyCustomerRepository;
    private final LoyaltyRedemptionRepository redemptionRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final CustomerRepository customerRepository;
    private final BusinessRepository businessRepository;
    private final ModuleAccessService moduleAccessService;
    private final ActivityLogService activityLogService;
    private final String frontendUrl;
    private final TransactionTemplate tx;

    public LoyaltyService(
            LoyaltyProgramRepository programRepository,
            LoyaltyCustomerRepository loyaltyCustomerRepository,
            LoyaltyRedemptionRepository redemptionRepository,
            SaleRepository saleRepository,
            SaleItemRepository saleItemRepository,
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            ServiceOrderRepository serviceOrderRepository,
            CustomerRepository customerRepository,
            BusinessRepository businessRepository,
            ModuleAccessService moduleAccessService,
            ActivityLogService activityLogService,
            @Value("${app.frontend-url}") String frontendUrl,
            PlatformTransactionManager transactionManager
    ) {
        this.programRepository = programRepository;
        this.loyaltyCustomerRepository = loyaltyCustomerRepository;
        this.redemptionRepository = redemptionRepository;
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.customerRepository = customerRepository;
        this.businessRepository = businessRepository;
        this.moduleAccessService = moduleAccessService;
        this.activityLogService = activityLogService;
        this.frontendUrl = frontendUrl;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ---- The program's settings ----

    public LoyaltyProgramResponse getProgram() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, MODULE);
        return toResponse(ensureProgram(businessId));
    }

    public LoyaltyProgramResponse updateProgram(LoyaltyProgramRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, MODULE);
        LoyaltyProgram saved = tx.execute(status -> {
            LoyaltyProgram program = ensureProgram(businessId);
            program.setEnabled(req.enabled());
            program.setVisitsForReward(req.visitsForReward());
            program.setRewardDescription(req.rewardDescription().trim());
            return programRepository.save(program);
        });
        activityLogService.log("Updated the loyalty program (" + saved.getVisitsForReward() + " visits for " + saved.getRewardDescription() + ")",
                "BUSINESS", businessId);
        return toResponse(saved);
    }

    // ---- Counting visits (called after a sale is paid / a service order is completed) ----

    /** Counts today's visit for the sale's customer if the sale qualifies. Returns true only if a NEW visit was counted. */
    public boolean recordVisitForSale(UUID saleId) {
        Sale sale = saleRepository.findById(saleId).orElse(null);
        if (sale == null || !"PAID".equals(sale.getPaymentStatus()) || sale.getCustomerId() == null) {
            return false;
        }
        if (!isActive(sale.getBusinessId())) {
            return false;
        }
        // A visit means a service was done: a sale of only products doesn't count.
        List<SaleItem> items = saleItemRepository.findAllBySaleId(saleId);
        boolean qualifies = items.stream()
                .anyMatch(i -> i.getItemType() == SaleItemType.SERVICE && countsForLoyalty(i.getServiceCatalogId()));
        return qualifies && recordVisit(sale.getBusinessId(), sale.getCustomerId(), SOURCE_SALE, saleId);
    }

    public boolean recordVisitForServiceOrder(UUID orderId) {
        ServiceOrder order = serviceOrderRepository.findById(orderId).orElse(null);
        if (order == null || order.getCustomerId() == null
                || (order.getStatus() != ServiceOrderStatus.COMPLETED && order.getStatus() != ServiceOrderStatus.PICKED_UP)) {
            return false;
        }
        if (!isActive(order.getBusinessId())) {
            return false;
        }
        return countsForLoyalty(order.getServiceCatalogId())
                && recordVisit(order.getBusinessId(), order.getCustomerId(), SOURCE_SERVICE_ORDER, orderId);
    }

    private boolean recordVisit(UUID businessId, UUID customerId, String sourceType, UUID sourceId) {
        Integer total = tx.execute(status -> {
            loyaltyCustomerRepository.createIfMissing(businessId, customerId, newToken());
            return loyaltyCustomerRepository.recordVisit(businessId, customerId, LocalDate.now(VISIT_ZONE), sourceType, sourceId);
        });
        return total != null;
    }

    // A line with no catalog entry (a free-form service) counts; a catalog service counts unless the owner switched it off.
    private boolean countsForLoyalty(UUID serviceCatalogId) {
        if (serviceCatalogId == null) {
            return true;
        }
        return serviceCatalogItemRepository.findById(serviceCatalogId).map(s -> s.isCountsForLoyalty()).orElse(true);
    }

    // ---- Reading a customer's standing ----

    /** For code that already knows the business (receipts, listeners) — no tenant needed. */
    public LoyaltyStatusResponse statusFor(UUID businessId, UUID customerId) {
        if (!isActive(businessId)) {
            return new LoyaltyStatusResponse(false, 0, 0, false, 0, null, null);
        }
        LoyaltyProgram program = ensureProgram(businessId);
        Optional<LoyaltyCustomerRepository.Standing> standing = loyaltyCustomerRepository.standing(businessId, customerId);
        int visits = standing.map(s -> s.getVisitsTotal() - s.getVisitsRedeemed()).orElse(0);
        return build(program, visits, standing.map(LoyaltyCustomerRepository.Standing::getToken).orElse(null));
    }

    /** The till's view of the customer the cashier has picked. */
    public LoyaltyStatusResponse statusForCustomer(UUID customerId) {
        UUID businessId = TenantContext.getBusinessId();
        requireOwnCustomer(businessId, customerId);
        return statusFor(businessId, customerId);
    }

    /** A line for the receipt text, e.g. "You currently have 7 points (reach 10 points to earn a reward)", or null when there is nothing to say. */
    public String receiptLine(UUID businessId, UUID customerId) {
        LoyaltyStatusResponse status = statusFor(businessId, customerId);
        if (!status.active() || status.pointsUrl() == null) {
            return null;
        }
        String have = "You currently have " + status.visits() + (status.visits() == 1 ? " point" : " points");
        return status.rewardDue()
                ? have + " - your reward is ready!"
                : have + " (reach " + status.target() + " points to earn a reward)";
    }

    /** The short form of the customer's points link for a text message (no https://, to save characters). */
    public String shortLink(UUID businessId, UUID customerId) {
        LoyaltyStatusResponse status = statusFor(businessId, customerId);
        if (!status.active() || status.pointsUrl() == null) {
            return null;
        }
        return status.pointsUrl().replaceFirst("^https?://", "");
    }

    // ---- Redeeming a reward ----

    public LoyaltyStatusResponse redeem(UUID customerId) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, MODULE);
        Customer customer = requireOwnCustomer(businessId, customerId);
        LoyaltyProgram program = ensureProgram(businessId);
        if (!program.isEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The loyalty program is switched off.");
        }

        boolean redeemed = Boolean.TRUE.equals(tx.execute(status -> {
            Integer total = loyaltyCustomerRepository.redeem(businessId, customerId, program.getVisitsForReward());
            if (total == null) {
                return false;
            }
            redemptionRepository.save(LoyaltyRedemption.builder()
                    .businessId(businessId).customerId(customerId)
                    .visitsConsumed(program.getVisitsForReward())
                    .rewardDescription(program.getRewardDescription())
                    .redeemedBy(TenantContext.getUserId())
                    .build());
            return true;
        }));
        if (!redeemed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, customer.getFullName() + " isn't due a reward yet.");
        }
        activityLogService.log("Redeemed a loyalty reward (" + program.getRewardDescription() + ") for " + customer.getFullName(),
                "CUSTOMER", customerId);
        return statusFor(businessId, customerId);
    }

    // ---- The owner's overview ----

    public List<LoyaltyCustomerRowResponse> overview() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, MODULE);
        LoyaltyProgram program = ensureProgram(businessId);
        List<LoyaltyCustomerRepository.Standing> standings = loyaltyCustomerRepository.standings(businessId);
        Map<UUID, Customer> customers = customerRepository.findAllById(
                        standings.stream().map(LoyaltyCustomerRepository.Standing::getCustomerId).toList())
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));

        int target = program.getVisitsForReward();
        return standings.stream()
                .filter(s -> customers.containsKey(s.getCustomerId()))
                .map(s -> {
                    int visits = s.getVisitsTotal() - s.getVisitsRedeemed();
                    Customer c = customers.get(s.getCustomerId());
                    return new LoyaltyCustomerRowResponse(c.getId(), c.getFullName(), c.getPhone(), visits, target,
                            visits >= target, visits / target, s.getVisitsTotal());
                })
                // Customers due a reward first, then closest to one.
                .sorted(Comparator.comparing(LoyaltyCustomerRowResponse::rewardDue).reversed()
                        .thenComparing(Comparator.comparingInt(LoyaltyCustomerRowResponse::visits).reversed()))
                .toList();
    }

    // ---- The customer's own page ----

    public PublicLoyaltyResponse publicPage(String token) {
        LoyaltyCustomer row = loyaltyCustomerRepository.findByToken(token == null ? "" : token.trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This link isn't valid."));
        if (!isActive(row.getBusinessId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This link isn't valid.");
        }
        Business business = businessRepository.findById(row.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This link isn't valid."));
        Customer customer = customerRepository.findById(row.getCustomerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This link isn't valid."));
        LoyaltyStatusResponse status = statusFor(row.getBusinessId(), row.getCustomerId());

        String name = customer.getFullName() == null ? "" : customer.getFullName().trim();
        String firstName = name.isEmpty() ? null : name.split("\\s+")[0];
        String bookingPath = moduleAccessService.hasModule(business.getId(), "BOOKINGS") ? "/book/" + business.getSlug() : null;
        return new PublicLoyaltyResponse(business.getName(), business.getLogoUrl(), firstName, status.visits(),
                status.target(), status.rewardDue(), status.rewardDescription(), bookingPath);
    }

    // ---- helpers ----

    /** The program exists and is on, for a business that has the loyalty module. */
    boolean isActive(UUID businessId) {
        return moduleAccessService.hasModule(businessId, MODULE) && ensureProgram(businessId).isEnabled();
    }

    private LoyaltyProgram ensureProgram(UUID businessId) {
        return tx.execute(status -> {
            programRepository.createIfMissing(businessId);
            return programRepository.findById(businessId).orElseThrow();
        });
    }

    private LoyaltyStatusResponse build(LoyaltyProgram program, int visits, String token) {
        int target = program.getVisitsForReward();
        return new LoyaltyStatusResponse(true, visits, target, visits >= target, visits / target,
                program.getRewardDescription(), token == null ? null : frontendUrl.replaceFirst("/+$", "") + "/p/" + token);
    }

    private LoyaltyProgramResponse toResponse(LoyaltyProgram program) {
        return new LoyaltyProgramResponse(program.isEnabled(), program.getVisitsForReward(), program.getRewardDescription());
    }

    private Customer requireOwnCustomer(UUID businessId, UUID customerId) {
        return customerRepository.findById(customerId)
                .filter(c -> c.getBusinessId().equals(businessId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found."));
    }

    private static String newToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(TOKEN_ALPHABET.charAt(RANDOM.nextInt(TOKEN_ALPHABET.length())));
        }
        return sb.toString();
    }
}
