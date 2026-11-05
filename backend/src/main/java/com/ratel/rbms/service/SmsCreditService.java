package com.ratel.rbms.service;

import com.ratel.rbms.dto.CheckoutResponse;
import com.ratel.rbms.dto.SmsBundleResponse;
import com.ratel.rbms.dto.SmsPricingResponse;
import com.ratel.rbms.entity.SmsPlatformSettings;
import com.ratel.rbms.repository.SmsPlatformSettingsRepository;
import com.ratel.rbms.dto.SmsPurchaseVerifyResponse;
import com.ratel.rbms.entity.SmsBundle;
import com.ratel.rbms.entity.SmsCreditLedger;
import com.ratel.rbms.entity.SmsCreditPurchase;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.SmsAccountRepository;
import com.ratel.rbms.repository.SmsBundleRepository;
import com.ratel.rbms.repository.SmsCreditPurchaseRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.sms.SmsGateway;
import com.ratel.rbms.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Buying SMS credits through Paystack — the same checkout shape as subscription
 * billing (initialise -> inline popup -> verify), but a purchase adds credits
 * instead of extending a period.
 *
 * Crediting is idempotent: the webhook, the browser's verify call and the
 * reconciliation sweep can all arrive for the same reference, in any order, and
 * the conditional UPDATE in markSuccessIfNotAlready lets exactly one of them
 * add the credits. The Paystack lookup happens outside any transaction.
 */
@Service
public class SmsCreditService {

    private static final Logger log = LoggerFactory.getLogger(SmsCreditService.class);

    /** Prefix on every SMS purchase reference — lets the shared Paystack webhook tell these apart from subscription payments. */
    public static final String REFERENCE_PREFIX = "SMS-";

    // A purchase Paystack still reports as unfinished after this long is given up on.
    private static final Duration PENDING_EXPIRY = Duration.ofHours(24);

    private final SmsBundleRepository bundleRepository;
    private final SmsPlatformSettingsRepository settingsRepository;
    private final SmsCreditPurchaseRepository purchaseRepository;
    private final SmsAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final PaystackService paystackService;
    private final ModuleAccessService moduleAccessService;
    private final SmsService smsService;
    private final SmsGateway gateway;
    private final TransactionTemplate tx;

    public SmsCreditService(
            SmsBundleRepository bundleRepository,
            SmsPlatformSettingsRepository settingsRepository,
            SmsCreditPurchaseRepository purchaseRepository,
            SmsAccountRepository accountRepository,
            UserRepository userRepository,
            PaystackService paystackService,
            ModuleAccessService moduleAccessService,
            SmsService smsService,
            SmsGateway gateway,
            PlatformTransactionManager transactionManager
    ) {
        this.bundleRepository = bundleRepository;
        this.settingsRepository = settingsRepository;
        this.purchaseRepository = purchaseRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.paystackService = paystackService;
        this.moduleAccessService = moduleAccessService;
        this.smsService = smsService;
        this.gateway = gateway;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public List<SmsBundleResponse> listBundles() {
        moduleAccessService.requireModule(TenantContext.getBusinessId(), SmsService.MODULE);
        return bundleRepository.findAllByActiveTrueOrderBySortOrderAscCreditsAsc().stream()
                .map(SmsBundleResponse::from)
                .toList();
    }

    /** Smallest and largest single top-up, in GHS. */
    public static final BigDecimal MIN_AMOUNT = new BigDecimal("5.00");
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("5000.00");

    /** The price of one credit and the allowed amounts, for the "Buy credits" screen. */
    public SmsPricingResponse pricing() {
        moduleAccessService.requireModule(TenantContext.getBusinessId(), SmsService.MODULE);
        return new SmsPricingResponse(sellPrice(), MIN_AMOUNT, MAX_AMOUNT, "GHS");
    }

    /** How many credits an amount buys at a price — whole credits only, the remainder is never charged for. */
    public static int creditsFor(BigDecimal amount, BigDecimal pricePerCredit) {
        return amount.divide(pricePerCredit, 0, RoundingMode.DOWN).intValueExact();
    }

    /** Buy by amount: the business types how much to spend and gets amount / price-per-credit credits. */
    public CheckoutResponse startPurchaseByAmount(BigDecimal requestedAmount) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, SmsService.MODULE);
        requireProvider();

        BigDecimal price = sellPrice();
        if (price == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Credits aren't on sale yet. Please contact support.");
        }
        BigDecimal amount = requestedAmount.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Enter an amount between GHS " + MIN_AMOUNT.toPlainString() + " and GHS " + MAX_AMOUNT.toPlainString() + ".");
        }
        int credits = creditsFor(amount, price);
        if (credits < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That amount is too small to buy a credit.");
        }
        return checkout(businessId, null, "Top-up", credits, amount, "amount");
    }

    public CheckoutResponse startPurchase(UUID bundleId) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, SmsService.MODULE);
        requireProvider();

        SmsBundle bundle = bundleRepository.findById(bundleId)
                .filter(SmsBundle::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This bundle isn't available."));
        return checkout(businessId, bundle.getId(), bundle.getName(), bundle.getCredits(), bundle.getPrice(), bundle.getId().toString());
    }

    private void requireProvider() {
        if (!gateway.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS isn't available right now. Please contact support.");
        }
    }

    private BigDecimal sellPrice() {
        return settingsRepository.findById(SmsPlatformSettings.ROW_ID).map(SmsPlatformSettings::getSellPricePerCredit).orElse(null);
    }

    private CheckoutResponse checkout(UUID businessId, UUID bundleId, String name, int credits, BigDecimal amount, String source) {
        User owner = userRepository.findById(TenantContext.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));

        String reference = REFERENCE_PREFIX + businessId.toString().substring(0, 8) + "-" + UUID.randomUUID().toString().substring(0, 8);
        long amountMinorUnits = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();

        PaystackService.InitResult init = paystackService.initializeTransaction(
                paystackService.resolvePlatformSecretKey(),
                owner.getEmail(),
                amountMinorUnits,
                reference,
                Map.of("businessId", businessId.toString(), "purpose", "sms_credits", "source", source)
        );

        // The name, credits and amount are copied onto the purchase so a later price change
        // can't alter what this payment is for.
        purchaseRepository.save(SmsCreditPurchase.builder()
                .businessId(businessId)
                .bundleId(bundleId)
                .bundleName(name)
                .credits(credits)
                .amount(amount)
                .paystackReference(init.reference())
                .build());

        return new CheckoutResponse(init.accessCode(), init.reference());
    }

    /** The owner's own "I've paid" check — only for a purchase made by their business. */
    public SmsPurchaseVerifyResponse verifyOwnPurchase(String reference) {
        SmsCreditPurchase purchase = purchaseRepository.findByPaystackReference(reference)
                .filter(p -> p.getBusinessId().equals(TenantContext.getBusinessId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown payment reference."));
        return verifyPurchase(purchase.getPaystackReference());
    }

    public boolean isSmsReference(String reference) {
        return reference != null && reference.startsWith(REFERENCE_PREFIX);
    }

    /** Pending purchases old enough that the customer has surely finished (or abandoned) paying — for the reconciliation sweep. */
    public List<String> findStalePendingReferences(Instant createdBefore) {
        return purchaseRepository.findAllByStatusAndCreatedAtBefore(SmsCreditPurchase.PENDING, createdBefore).stream()
                .map(SmsCreditPurchase::getPaystackReference)
                .toList();
    }

    /**
     * Confirms a purchase with Paystack and, if it was paid, adds the credits —
     * exactly once. Safe to call repeatedly and from anywhere (webhook, browser,
     * sweep), so it takes no tenant context.
     */
    public SmsPurchaseVerifyResponse verifyPurchase(String reference) {
        SmsCreditPurchase purchase = purchaseRepository.findByPaystackReference(reference)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown payment reference."));

        if (SmsCreditPurchase.SUCCESS.equals(purchase.getStatus())) {
            return new SmsPurchaseVerifyResponse(true, smsService.balanceOf(purchase.getBusinessId()), "Already confirmed.");
        }

        PaystackService.VerifyResult verify = paystackService.verifyTransaction(paystackService.resolvePlatformSecretKey(), reference);

        if (!verify.success()) {
            boolean definitelyFailed = "failed".equalsIgnoreCase(verify.status())
                    || (purchase.getCreatedAt() != null && purchase.getCreatedAt().isBefore(Instant.now().minus(PENDING_EXPIRY)));
            if (definitelyFailed) {
                purchaseRepository.markFailedIfPending(reference);
            }
            return new SmsPurchaseVerifyResponse(false, smsService.balanceOf(purchase.getBusinessId()),
                    "Payment wasn't completed (" + verify.status() + ").");
        }

        // Never credit more than was actually paid: the amount Paystack collected
        // must match what this purchase was created for.
        long expectedMinorUnits = purchase.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (verify.amountMinorUnits() != expectedMinorUnits || !"GHS".equalsIgnoreCase(verify.currency())) {
            log.error("SMS purchase {} amount mismatch: expected {} GHS minor units, Paystack reported {} {}",
                    reference, expectedMinorUnits, verify.amountMinorUnits(), verify.currency());
            return new SmsPurchaseVerifyResponse(false, smsService.balanceOf(purchase.getBusinessId()),
                    "We couldn't match this payment to your order. Please contact support.");
        }

        Integer balance = tx.execute(status -> {
            int flipped = purchaseRepository.markSuccessIfNotAlready(reference, Instant.now());
            if (flipped == 0) {
                // A racing call credited it while we were checking with Paystack.
                return null;
            }
            smsService.ensureAccount(purchase.getBusinessId());
            int newBalance = accountRepository.credit(purchase.getBusinessId(), purchase.getCredits());
            smsService.writeLedger(purchase.getBusinessId(), purchase.getCredits(), newBalance, SmsCreditLedger.PURCHASE,
                    reference, "Bought " + purchase.getBundleName() + " (" + purchase.getCredits() + " credits)", null);
            return newBalance;
        });

        if (balance == null) {
            return new SmsPurchaseVerifyResponse(true, smsService.balanceOf(purchase.getBusinessId()), "Already confirmed.");
        }
        return new SmsPurchaseVerifyResponse(true, balance, "Credits added.");
    }
}
