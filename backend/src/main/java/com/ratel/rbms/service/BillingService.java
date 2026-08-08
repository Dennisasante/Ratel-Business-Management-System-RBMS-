package com.ratel.rbms.service;

import com.ratel.rbms.dto.BillingStatusResponse;
import com.ratel.rbms.dto.CheckoutResponse;
import com.ratel.rbms.dto.SubscriptionPaymentResponse;
import com.ratel.rbms.dto.SubscriptionPlanResponse;
import com.ratel.rbms.dto.VerifyPaymentResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.SubscriptionPayment;
import com.ratel.rbms.entity.SubscriptionPlan;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.BillingStatus;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.PlatformBillingSettingsRepository;
import com.ratel.rbms.repository.SubscriptionPaymentRepository;
import com.ratel.rbms.repository.SubscriptionPlanRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business-facing billing logic. Owns the one rule that actually matters for
 * money: a payment's period is only ever applied to a business once, no matter
 * how many times verifyPayment(reference) gets called for it (webhook, client
 * retry, both racing each other — see the locking/guard notes inline below).
 */
@Service
public class BillingService {

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final PaystackService paystackService;
    private final ActivityLogService activityLogService;
    private final PlatformBillingSettingsRepository platformBillingSettingsRepository;
    private final String platformPaystackSecretKey;

    public BillingService(
            BusinessRepository businessRepository,
            UserRepository userRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            SubscriptionPaymentRepository subscriptionPaymentRepository,
            PaystackService paystackService,
            ActivityLogService activityLogService,
            PlatformBillingSettingsRepository platformBillingSettingsRepository,
            @Value("${app.paystack.secret-key}") String platformPaystackSecretKey
    ) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.subscriptionPaymentRepository = subscriptionPaymentRepository;
        this.paystackService = paystackService;
        this.activityLogService = activityLogService;
        this.platformBillingSettingsRepository = platformBillingSettingsRepository;
        this.platformPaystackSecretKey = platformPaystackSecretKey;
    }

    public BillingStatusResponse getStatus() {
        Business business = getOwnBusiness();
        return buildStatusResponse(business);
    }

    public List<SubscriptionPlanResponse> listPlans() {
        return subscriptionPlanRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
                .map(SubscriptionPlanResponse::from)
                .toList();
    }

    public List<SubscriptionPaymentResponse> history() {
        UUID businessId = TenantContext.getBusinessId();
        return subscriptionPaymentRepository.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(p -> SubscriptionPaymentResponse.from(p, planNameOrNull(p.getSubscriptionPlanId())))
                .toList();
    }

    @Transactional
    public CheckoutResponse startCheckout(UUID planId) {
        Business business = getOwnBusiness();

        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This plan isn't available."));

        User owner = userRepository.findById(TenantContext.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));

        String reference = "RATEL-" + business.getId().toString().substring(0, 8) + "-" + UUID.randomUUID().toString().substring(0, 8);
        long amountMinorUnits = plan.getPrice().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();

        PaystackService.InitResult init = paystackService.initializeTransaction(
                platformPaystackSecretKey,
                owner.getEmail(),
                amountMinorUnits,
                reference,
                Map.of("businessId", business.getId().toString(), "planId", plan.getId().toString())
        );

        SubscriptionPayment payment = SubscriptionPayment.builder()
                .businessId(business.getId())
                .subscriptionPlanId(plan.getId())
                .amount(plan.getPrice())
                .currency(plan.getCurrency())
                .paystackReference(init.reference())
                .status("PENDING")
                .build();
        subscriptionPaymentRepository.save(payment);

        return new CheckoutResponse(init.accessCode(), init.reference());
    }

    /**
     * Always re-verifies with Paystack directly — never trusts that the popup
     * reported "success" client-side. Safe to call more than once (webhook +
     * a client-side verify racing each other, or a user refreshing mid-flow):
     * only the first call that actually flips the payment row to SUCCESS goes
     * on to extend the business's period; everyone else is a no-op that just
     * reports the already-current state.
     */
    @Transactional
    public VerifyPaymentResponse verifyPayment(String reference) {
        SubscriptionPayment payment = subscriptionPaymentRepository.findByPaystackReference(reference)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown payment reference."));

        if ("SUCCESS".equals(payment.getStatus())) {
            Business business = businessRepository.findById(payment.getBusinessId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
            return new VerifyPaymentResponse(true, business.getBillingStatus().name(), business.getCurrentPeriodEndsAt(), "Already confirmed.");
        }

        PaystackService.VerifyResult verify = paystackService.verifyTransaction(platformPaystackSecretKey, reference);

        if (!verify.success()) {
            payment.setStatus("FAILED");
            subscriptionPaymentRepository.save(payment);
            return new VerifyPaymentResponse(false, null, null, "Payment wasn't completed (" + verify.status() + ").");
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(payment.getSubscriptionPlanId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Plan for this payment no longer exists."));

        // Lock the business row for the rest of this transaction: if a racing
        // call (webhook vs. client verify) is also inside this method for the
        // same business, one of them blocks here until the other commits.
        Business business = businessRepository.findByIdForUpdate(payment.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));

        Instant now = Instant.now();
        Instant periodStart = (business.getCurrentPeriodEndsAt() != null && business.getCurrentPeriodEndsAt().isAfter(now))
                ? business.getCurrentPeriodEndsAt()
                : now;
        Instant periodEnd = periodStart.plus(plan.getBillingPeriodDays(), ChronoUnit.DAYS);

        int updated = subscriptionPaymentRepository.markSuccessIfNotAlready(reference, now, periodStart, periodEnd);
        if (updated == 0) {
            // A racing call already recorded this exact payment while we were
            // blocked waiting for the lock above — the business row we're
            // holding is already the post-extension version, so just report it.
            return new VerifyPaymentResponse(true, business.getBillingStatus().name(), business.getCurrentPeriodEndsAt(), "Already confirmed.");
        }

        business.setCurrentPeriodEndsAt(periodEnd);
        business.setSubscriptionPlanId(plan.getId());
        business.setBillingStatus(BillingStatus.ACTIVE);
        // Reset so BillingExpiryService's reminder guard applies to *this* new
        // deadline, not the last one this business was ever reminded about.
        business.setExpiryReminderSentAt(null);
        businessRepository.save(business);

        // TenantContext.getUserId() is null when this runs from the unauthenticated
        // webhook path rather than a logged-in Owner's verify call — both are valid.
        activityLogService.log(business.getId(), TenantContext.getUserId(),
                "Subscription payment confirmed — \"" + plan.getName() + "\", now active until " + periodEnd,
                "SUBSCRIPTION_PAYMENT", payment.getId());

        return new VerifyPaymentResponse(true, BillingStatus.ACTIVE.name(), periodEnd, "Payment confirmed.");
    }

    private Business getOwnBusiness() {
        return businessRepository.findById(TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
    }

    private String planNameOrNull(UUID planId) {
        return subscriptionPlanRepository.findById(planId).map(SubscriptionPlan::getName).orElse(null);
    }

    private BillingStatusResponse buildStatusResponse(Business business) {
        SubscriptionPlanResponse plan = business.getSubscriptionPlanId() != null
                ? subscriptionPlanRepository.findById(business.getSubscriptionPlanId()).map(SubscriptionPlanResponse::from).orElse(null)
                : null;

        Instant deadline = business.getBillingStatus() == BillingStatus.TRIALING
                ? business.getTrialEndsAt()
                : business.getCurrentPeriodEndsAt();
        long daysRemaining = deadline != null ? ChronoUnit.DAYS.between(Instant.now(), deadline) : 0;

        return new BillingStatusResponse(
                business.getBillingStatus().name(),
                plan,
                business.getTrialEndsAt(),
                business.getCurrentPeriodEndsAt(),
                daysRemaining,
                platformBillingSettingsRepository.findFirstByOrderByUpdatedAtDesc().getUsdDisplayRate()
        );
    }
}
