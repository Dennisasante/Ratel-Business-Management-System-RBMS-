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
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.PlatformBillingSettingsRepository;
import com.ratel.rbms.repository.SubscriptionPaymentRepository;
import com.ratel.rbms.repository.SubscriptionPlanRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
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
    private final EmailService emailService;

    public BillingService(
            BusinessRepository businessRepository,
            UserRepository userRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            SubscriptionPaymentRepository subscriptionPaymentRepository,
            PaystackService paystackService,
            ActivityLogService activityLogService,
            PlatformBillingSettingsRepository platformBillingSettingsRepository,
            EmailService emailService
    ) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.subscriptionPaymentRepository = subscriptionPaymentRepository;
        this.paystackService = paystackService;
        this.activityLogService = activityLogService;
        this.platformBillingSettingsRepository = platformBillingSettingsRepository;
        this.emailService = emailService;
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
    public CheckoutResponse startCheckout(UUID planId, int months, boolean saveCard) {
        Business business = getOwnBusiness();

        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This plan isn't available."));

        User owner = userRepository.findById(TenantContext.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));

        BigDecimal monthlyRate = business.getPriceOverride() != null ? business.getPriceOverride() : plan.getPrice();
        BigDecimal effectivePrice = computeTotal(monthlyRate, months);

        String reference = "RATEL-" + business.getId().toString().substring(0, 8) + "-" + UUID.randomUUID().toString().substring(0, 8);
        long amountMinorUnits = effectivePrice.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();

        PaystackService.InitResult init = paystackService.initializeTransaction(
                paystackService.resolvePlatformSecretKey(),
                owner.getEmail(),
                amountMinorUnits,
                reference,
                Map.of("businessId", business.getId().toString(), "planId", plan.getId().toString(), "months", String.valueOf(months))
        );

        SubscriptionPayment payment = SubscriptionPayment.builder()
                .businessId(business.getId())
                .subscriptionPlanId(plan.getId())
                .amount(effectivePrice)
                .currency(plan.getCurrency())
                .months(months)
                .paystackReference(init.reference())
                .status("PENDING")
                .saveCardRequested(saveCard)
                .build();
        subscriptionPaymentRepository.save(payment);

        return new CheckoutResponse(init.accessCode(), init.reference());
    }

    // 1/3/6/12 months only. Mirrored in frontend/app/dashboard/billing/page.tsx's
    // MONTH_OPTIONS for pre-checkout price display — keep both in sync if these
    // tiers ever change.
    private static final Map<Integer, BigDecimal> DISCOUNT_BY_MONTHS = Map.of(
            1, BigDecimal.ZERO,
            3, new BigDecimal("0.05"),
            6, new BigDecimal("0.10"),
            12, new BigDecimal("0.20")
    );

    private static BigDecimal discountForMonths(int months) {
        BigDecimal discount = DISCOUNT_BY_MONTHS.get(months);
        if (discount == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose 1, 3, 6, or 12 months.");
        }
        return discount;
    }

    // effectiveMonthlyRate x months x (1 - discount), rounded to cents.
    private static BigDecimal computeTotal(BigDecimal effectiveMonthlyRate, int months) {
        BigDecimal discount = discountForMonths(months);
        return effectiveMonthlyRate
                .multiply(BigDecimal.valueOf(months))
                .multiply(BigDecimal.ONE.subtract(discount))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // Auto-renewal via a saved card — a separate transactional path from
    // startCheckout()/verifyPayment() above, but every success/failure it
    // produces is funneled through verifyPayment() itself (see below) so
    // there's exactly one place that ever extends a business's period.
    @Transactional
    public void attemptAutoCharge(Business business) {
        if (business.getPaystackAuthorizationCode() == null || business.getPaystackAuthorizationCode().isBlank()) {
            return;
        }
        if (business.getSubscriptionPlanId() == null) {
            return;
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(business.getSubscriptionPlanId()).orElse(null);
        if (plan == null || !plan.isActive()) {
            return;
        }

        User owner = userRepository.findAllByBusinessIdAndRole(business.getId(), Role.OWNER).stream()
                .filter(User::isActive)
                .findFirst()
                .orElse(null);
        if (owner == null) {
            return;
        }

        // Repeats whatever cycle length the business last checked out with —
        // see verifyPayment()'s billingCycleMonths comment — so a saved-card
        // renewal keeps the same discount tier instead of collapsing to 1 month.
        int months = business.getBillingCycleMonths();
        BigDecimal monthlyRate = business.getPriceOverride() != null ? business.getPriceOverride() : plan.getPrice();
        BigDecimal effectivePrice = computeTotal(monthlyRate, months);
        long amountMinorUnits = effectivePrice.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        String reference = "RATEL-AUTO-" + business.getId().toString().substring(0, 8) + "-" + UUID.randomUUID().toString().substring(0, 8);

        // Pre-create the PENDING row exactly like startCheckout() does — verifyPayment()
        // below looks this row up by reference the same way it would for a manual checkout.
        SubscriptionPayment payment = SubscriptionPayment.builder()
                .businessId(business.getId())
                .subscriptionPlanId(plan.getId())
                .amount(effectivePrice)
                .currency(plan.getCurrency())
                .months(months)
                .paystackReference(reference)
                .status("PENDING")
                .build();
        subscriptionPaymentRepository.save(payment);

        PaystackService.ChargeResult charge;
        try {
            charge = paystackService.chargeAuthorization(
                    paystackService.resolvePlatformSecretKey(),
                    business.getPaystackAuthorizationCode(),
                    owner.getEmail(),
                    amountMinorUnits,
                    reference
            );
        } catch (ApiException e) {
            // Paystack unreachable — leave the PENDING row as-is and let the
            // grace-period sweep (which runs right after this in
            // BillingExpiryService) handle it like any other unrenewed period.
            return;
        }

        if (!charge.success()) {
            activityLogService.log(business.getId(), null,
                    "Automatic renewal charge failed (" + charge.status() + ") — grace period applies as usual", "BUSINESS", business.getId());
            emailService.sendAutoChargeFailed(owner.getEmail(), business.getName());
            return;
        }

        // Same "never trust the caller, always re-verify with Paystack" rule
        // as a manual checkout — and, critically, the same markSuccessIfNotAlready
        // guard, so this can never double-credit a period even if a webhook for
        // this same reference lands concurrently.
        verifyPayment(reference);
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

        PaystackService.VerifyResult verify = paystackService.verifyTransaction(paystackService.resolvePlatformSecretKey(), reference);

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
        // payment.getMonths() is safe to read here (same reasoning as
        // payment.isSaveCardRequested() below) — set once at checkout/
        // attemptAutoCharge time and untouched by markSuccessIfNotAlready()'s
        // bulk update.
        Instant periodEnd = periodStart.plus((long) plan.getBillingPeriodDays() * payment.getMonths(), ChronoUnit.DAYS);

        int updated = subscriptionPaymentRepository.markSuccessIfNotAlready(reference, now, periodStart, periodEnd);
        if (updated == 0) {
            // A racing call already recorded this exact payment while we were
            // blocked waiting for the lock above — the business row we're
            // holding is already the post-extension version, so just report it.
            return new VerifyPaymentResponse(true, business.getBillingStatus().name(), business.getCurrentPeriodEndsAt(), "Already confirmed.");
        }

        if (verify.authorizationCode() != null) {
            subscriptionPaymentRepository.updateAuthorizationCode(reference, verify.authorizationCode());
            // Independent of autoRenewEnabled below — this just keeps the saved
            // card current whenever a reusable one comes back, whether or not
            // this specific checkout asked to enable auto-renewal.
            business.setPaystackAuthorizationCode(verify.authorizationCode());
            business.setCardLast4(verify.cardLast4());
            business.setCardBrand(verify.cardBrand());
            // payment.isSaveCardRequested() is safe to read here (unlike its
            // status/paidAt/period fields) — that column was set once at
            // checkout and untouched by markSuccessIfNotAlready()'s bulk update.
            if (payment.isSaveCardRequested()) {
                business.setAutoRenewEnabled(true);
            }
        }

        business.setCurrentPeriodEndsAt(periodEnd);
        business.setSubscriptionPlanId(plan.getId());
        // Recorded so a saved-card renewal (attemptAutoCharge()) repeats the
        // same cycle length/discount this payment was for, instead of
        // collapsing every renewal to a single month.
        business.setBillingCycleMonths(payment.getMonths());
        business.setBillingStatus(BillingStatus.ACTIVE);
        // Clears whatever grace window this business may have been in — a
        // renewal always supersedes it, whether or not it was actually GRACE.
        business.setGracePeriodEndsAt(null);
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

    @Transactional
    public void setAutoRenew(boolean enabled) {
        Business business = getOwnBusiness();
        if (enabled && (business.getPaystackAuthorizationCode() == null || business.getPaystackAuthorizationCode().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Save a card first by paying with Paystack and checking \"save this card\".");
        }
        business.setAutoRenewEnabled(enabled);
        businessRepository.save(business);
    }

    @Transactional
    public void removeSavedCard() {
        Business business = getOwnBusiness();
        business.setPaystackAuthorizationCode(null);
        business.setCardLast4(null);
        business.setCardBrand(null);
        // No card left to charge, so auto-renewal can't stay on.
        business.setAutoRenewEnabled(false);
        businessRepository.save(business);
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
                : business.getBillingStatus() == BillingStatus.GRACE
                ? business.getGracePeriodEndsAt()
                : business.getCurrentPeriodEndsAt();
        long daysRemaining = deadline != null ? ChronoUnit.DAYS.between(Instant.now(), deadline) : 0;

        BigDecimal effectiveMonthlyRate = business.getPriceOverride() != null
                ? business.getPriceOverride()
                : (plan != null ? plan.price() : null);

        return new BillingStatusResponse(
                business.getBillingStatus().name(),
                plan,
                business.getTrialEndsAt(),
                business.getCurrentPeriodEndsAt(),
                business.getGracePeriodEndsAt(),
                daysRemaining,
                business.getCardLast4(),
                business.getCardBrand(),
                business.isAutoRenewEnabled(),
                platformBillingSettingsRepository.findFirstByOrderByUpdatedAtDesc().getUsdDisplayRate(),
                effectiveMonthlyRate
        );
    }
}
