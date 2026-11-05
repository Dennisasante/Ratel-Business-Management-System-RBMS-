package com.ratel.rbms.service;

import com.ratel.rbms.dto.SmsBundleRequest;
import com.ratel.rbms.dto.SmsCreditAdjustRequest;
import com.ratel.rbms.dto.SmsPlatformAccountResponse;
import com.ratel.rbms.dto.SmsPlatformBundleResponse;
import com.ratel.rbms.dto.SmsPlatformSettingsRequest;
import com.ratel.rbms.dto.SmsPlatformOverviewResponse;
import com.ratel.rbms.dto.SmsSenderUpdateRequest;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessSmsAccount;
import com.ratel.rbms.entity.SmsBundle;
import com.ratel.rbms.entity.SmsCreditLedger;
import com.ratel.rbms.entity.SmsPlatformSettings;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.SmsAccountRepository;
import com.ratel.rbms.repository.SmsBundleRepository;
import com.ratel.rbms.repository.SmsPlatformSettingsRepository;
import com.ratel.rbms.sms.SmsGateway;
import com.ratel.rbms.sms.SmsProviderKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Super Admin side of SMS: the provider's health, the bundles on sale, and each business's sender name and credits. */
@Service
public class SmsPlatformService {

    private final SmsGateway gateway;
    private final SmsProviderKeyService providerKeyService;
    private final SmsBundleRepository bundleRepository;
    private final SmsPlatformSettingsRepository settingsRepository;
    private final SmsAccountRepository accountRepository;
    private final BusinessRepository businessRepository;
    private final SmsService smsService;
    private final PlatformAuditLogService auditLogService;
    private final TransactionTemplate tx;

    public SmsPlatformService(
            SmsGateway gateway,
            SmsProviderKeyService providerKeyService,
            SmsBundleRepository bundleRepository,
            SmsPlatformSettingsRepository settingsRepository,
            SmsAccountRepository accountRepository,
            BusinessRepository businessRepository,
            SmsService smsService,
            PlatformAuditLogService auditLogService,
            PlatformTransactionManager transactionManager
    ) {
        this.gateway = gateway;
        this.providerKeyService = providerKeyService;
        this.bundleRepository = bundleRepository;
        this.settingsRepository = settingsRepository;
        this.accountRepository = accountRepository;
        this.businessRepository = businessRepository;
        this.smsService = smsService;
        this.auditLogService = auditLogService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public SmsPlatformOverviewResponse overview() {
        var balance = gateway.balance();
        SmsProviderKeyService.Description key = providerKeyService.describe();
        return new SmsPlatformOverviewResponse(
                gateway.name(),
                gateway.isConfigured(),
                balance.map(SmsGateway.ProviderBalance::balance).orElse(null),
                balance.map(SmsGateway.ProviderBalance::bonus).orElse(null),
                accountRepository.totalOutstandingCredits(),
                providerCost(),
                key.source().name(),
                key.hint());
    }

    /** Saves (or, if blank, removes) the provider API key. The key itself is never logged or sent back. */
    public void updateProviderKey(UUID adminId, String apiKey) {
        String key = apiKey == null ? "" : apiKey.trim();
        if (!key.isEmpty() && key.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That doesn't look like a full API key — paste the whole thing.");
        }
        SmsPlatformSettings settings = settingsRepository.findById(SmsPlatformSettings.ROW_ID).orElseGet(SmsPlatformSettings::new);
        settings.setMnotifyApiKey(key.isEmpty() ? null : key);
        try {
            settingsRepository.save(settings);
        } catch (RuntimeException e) {
            // Secrets are only ever stored encrypted: with no ENCRYPTION_KEY on the server the save is refused.
            if (String.valueOf(rootMessage(e)).contains("ENCRYPTION_KEY")) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "This server has no ENCRYPTION_KEY set, so it can't store the key safely. Set one on the server, "
                                + "or put MNOTIFY_API_KEY in the server's .env instead.");
            }
            throw e;
        }
        auditLogService.log(adminId, key.isEmpty() ? "SMS provider API key removed" : "SMS provider API key updated", null, null, null);
    }

    private BigDecimal providerCost() {
        return settingsRepository.findById(SmsPlatformSettings.ROW_ID).map(SmsPlatformSettings::getProviderCostPerCredit).orElse(null);
    }

    // What the provider charges Tallia per credit — the basis for every profit figure on this page.
    public void updateSettings(UUID adminId, SmsPlatformSettingsRequest req) {
        SmsPlatformSettings settings = settingsRepository.findById(SmsPlatformSettings.ROW_ID).orElseGet(SmsPlatformSettings::new);
        settings.setProviderCostPerCredit(req.providerCostPerCredit());
        settingsRepository.save(settings);
        auditLogService.log(adminId, "SMS provider cost per credit set to "
                + (req.providerCostPerCredit() == null ? "(none)" : req.providerCostPerCredit().toPlainString()), null, null, null);
    }

    // ---- Bundles ----

    public List<SmsPlatformBundleResponse> listBundles() {
        BigDecimal cost = providerCost();
        return bundleRepository.findAllByOrderBySortOrderAscCreditsAsc().stream()
                .map(b -> SmsPlatformBundleResponse.from(b, cost)).toList();
    }

    public SmsPlatformBundleResponse createBundle(UUID adminId, SmsBundleRequest req) {
        SmsBundle saved = bundleRepository.save(SmsBundle.builder()
                .name(req.name().trim())
                .credits(req.credits())
                .price(req.price())
                .active(req.active() == null || req.active())
                .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
                .build());
        auditLogService.log(adminId, "SMS bundle created: " + saved.getName(), null, null, null);
        return SmsPlatformBundleResponse.from(saved, providerCost());
    }

    // Past purchases keep the name/credits/price they were made at, so editing
    // a bundle only ever affects future sales.
    public SmsPlatformBundleResponse updateBundle(UUID adminId, UUID bundleId, SmsBundleRequest req) {
        SmsBundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bundle not found."));
        bundle.setName(req.name().trim());
        bundle.setCredits(req.credits());
        bundle.setPrice(req.price());
        if (req.active() != null) bundle.setActive(req.active());
        if (req.sortOrder() != null) bundle.setSortOrder(req.sortOrder());
        SmsBundle saved = bundleRepository.save(bundle);
        auditLogService.log(adminId, "SMS bundle updated: " + saved.getName(), null, null, null);
        return SmsPlatformBundleResponse.from(saved, providerCost());
    }

    // ---- Per-business accounts ----

    // Every business that has SMS switched on — whether or not they've touched
    // it yet — so a sender name can be set up before their first visit.
    public List<SmsPlatformAccountResponse> accounts() {
        return businessRepository.findAll().stream()
                .filter(b -> b.getEnabledModules().contains(SmsService.MODULE))
                .map(b -> {
                    BusinessSmsAccount account = accountRepository.findById(b.getId()).orElse(null);
                    return new SmsPlatformAccountResponse(
                            b.getId(), b.getName(),
                            account == null ? null : account.getSenderId(),
                            account == null ? BusinessSmsAccount.SENDER_NONE : account.getSenderStatus(),
                            smsService.balanceOf(b.getId()), null);
                })
                .sorted((a, b) -> a.businessName().compareToIgnoreCase(b.businessName()))
                .toList();
    }

    public SmsPlatformAccountResponse updateSender(UUID adminId, UUID businessId, SmsSenderUpdateRequest req) {
        Business business = findBusiness(businessId);
        String senderId = req.senderId() == null || req.senderId().isBlank() ? null : req.senderId().trim();
        if (!BusinessSmsAccount.SENDER_NONE.equals(req.senderStatus()) && senderId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a sender name before marking it pending or approved.");
        }
        tx.executeWithoutResult(status -> {
            BusinessSmsAccount account = smsService.ensureAccount(businessId);
            account.setSenderId(senderId);
            account.setSenderStatus(req.senderStatus());
            accountRepository.save(account);
        });
        auditLogService.log(adminId, "SMS sender name set to " + (senderId == null ? "(none)" : senderId)
                + " [" + req.senderStatus() + "]", businessId, business.getName(), null);
        // Saving a name asks the provider about it straight away, so the status reflects
        // what the provider actually says rather than what someone typed.
        String note = senderId != null && gateway.isConfigured() ? checkWithProvider(adminId, business, senderId) : null;
        return accountResponse(business, note);
    }

    /** "Check again" - e.g. once mNotify has had time to approve a name that was pending. */
    public SmsPlatformAccountResponse verifySender(UUID adminId, UUID businessId) {
        Business business = findBusiness(businessId);
        BusinessSmsAccount account = smsService.ensureAccount(businessId);
        if (account.getSenderId() == null || account.getSenderId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a sender name first.");
        }
        if (!gateway.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The SMS provider is not configured.");
        }
        return accountResponse(business, checkWithProvider(adminId, business, account.getSenderId()));
    }

    // Applies the provider's answer to the account. UNKNOWN (could not tell) never changes
    // the status - a flaky answer must not un-approve a working sender name.
    private String checkWithProvider(UUID adminId, Business business, String senderId) {
        SmsGateway.SenderCheck check = gateway.checkSender(senderId);
        String newStatus = switch (check.state()) {
            case APPROVED -> BusinessSmsAccount.SENDER_APPROVED;
            case PENDING -> BusinessSmsAccount.SENDER_PENDING;
            case NOT_FOUND -> BusinessSmsAccount.SENDER_NONE;
            case UNKNOWN -> null;
        };
        if (newStatus == null) {
            return "Could not confirm with " + gateway.name() + ": " + check.detail() + " The status was left as it was.";
        }
        tx.executeWithoutResult(status -> {
            BusinessSmsAccount account = smsService.ensureAccount(business.getId());
            account.setSenderStatus(newStatus);
            accountRepository.save(account);
        });
        auditLogService.log(adminId, "SMS sender name " + senderId + " checked with " + gateway.name() + ": " + newStatus,
                business.getId(), business.getName(), null);
        return "Checked with " + gateway.name() + ": " + check.detail();
    }

    // Positive grants credits (a goodwill top-up, a support fix), negative takes
    // them back. Always on the ledger with who did it and why.
    public SmsPlatformAccountResponse adjustCredits(UUID adminId, UUID businessId, SmsCreditAdjustRequest req) {
        Business business = findBusiness(businessId);
        int delta = req.credits();
        if (delta == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a number of credits other than zero.");
        }
        tx.executeWithoutResult(status -> {
            smsService.ensureAccount(businessId);
            Integer balanceAfter = delta > 0
                    ? accountRepository.credit(businessId, delta)
                    : accountRepository.debit(businessId, -delta);
            if (balanceAfter == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "That's more than this business has — their balance can't go below zero.");
            }
            smsService.writeLedger(businessId, delta, balanceAfter, SmsCreditLedger.ADJUSTMENT, null, req.reason().trim(), adminId);
        });
        auditLogService.log(adminId, "SMS credits adjusted by " + delta + ": " + req.reason().trim(),
                businessId, business.getName(), null);
        return accountResponse(business, null);
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    private Business findBusiness(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        if (!business.getEnabledModules().contains(SmsService.MODULE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Turn the SMS module on for this business first.");
        }
        return business;
    }

    private SmsPlatformAccountResponse accountResponse(Business business, String note) {
        BusinessSmsAccount account = accountRepository.findById(business.getId()).orElseThrow();
        return new SmsPlatformAccountResponse(business.getId(), business.getName(), account.getSenderId(),
                account.getSenderStatus(), smsService.balanceOf(business.getId()), note);
    }
}
