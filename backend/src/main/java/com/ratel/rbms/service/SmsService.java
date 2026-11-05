package com.ratel.rbms.service;

import com.ratel.rbms.dto.SmsAccountResponse;
import com.ratel.rbms.dto.SmsLedgerResponse;
import com.ratel.rbms.dto.SmsMessageResponse;
import com.ratel.rbms.dto.SmsSendTestRequest;
import com.ratel.rbms.entity.BusinessSmsAccount;
import com.ratel.rbms.entity.SmsCreditLedger;
import com.ratel.rbms.entity.SmsMessage;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.SmsAccountRepository;
import com.ratel.rbms.repository.SmsCreditLedgerRepository;
import com.ratel.rbms.repository.SmsMessageRepository;
import com.ratel.rbms.sms.SmsGateway;
import com.ratel.rbms.sms.SmsSegmentCalculator;
import com.ratel.rbms.tenant.TenantContext;
import com.ratel.rbms.util.PhoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The one place an SMS is actually sent — every feature that texts a customer
 * (the test send today; loyalty messages and campaigns later) goes through
 * send(), so the money rules live in exactly one spot:
 *
 *  1. Credits are reserved FIRST, in one conditional UPDATE, before the
 *     provider is ever called — a business can never send more than it has
 *     paid for, even with sends racing each other.
 *  2. The provider call happens OUTSIDE any database transaction (a slow
 *     provider must never hold a connection or a row lock).
 *  3. If the provider rejects or can't be reached, the credits are refunded
 *     and the message is marked FAILED — a failed send never costs the business.
 *
 * If the process dies between steps 1 and 3 the message stays QUEUED with its
 * credits spent; that is the safe direction to fail (never an unpaid send), and
 * the ledger makes any such row easy to find and refund.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    static final String MODULE = "SMS";
    static final int MAX_MESSAGE_LENGTH = 1000;

    private final SmsGateway gateway;
    private final SmsAccountRepository accountRepository;
    private final SmsMessageRepository messageRepository;
    private final SmsCreditLedgerRepository ledgerRepository;
    private final ModuleAccessService moduleAccessService;
    private final NotificationService notificationService;
    private final TransactionTemplate tx;

    public SmsService(
            SmsGateway gateway,
            SmsAccountRepository accountRepository,
            SmsMessageRepository messageRepository,
            SmsCreditLedgerRepository ledgerRepository,
            ModuleAccessService moduleAccessService,
            NotificationService notificationService,
            PlatformTransactionManager transactionManager
    ) {
        this.gateway = gateway;
        this.accountRepository = accountRepository;
        this.messageRepository = messageRepository;
        this.ledgerRepository = ledgerRepository;
        this.moduleAccessService = moduleAccessService;
        this.notificationService = notificationService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ---- Business-facing reads ----

    public SmsAccountResponse getAccount() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, MODULE);
        BusinessSmsAccount account = ensureAccount(businessId);
        int balance = balanceOf(businessId);
        return new SmsAccountResponse(
                account.getSenderId(), account.getSenderStatus(), account.senderApproved(),
                balance, account.getLowBalanceThreshold(), balance <= account.getLowBalanceThreshold(),
                gateway.isConfigured(), account.getReceiptMode());
    }

    /** PAPER / SMS / BOTH — whether customers also get a text receipt after a sale. */
    public SmsAccountResponse setReceiptMode(String mode) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, MODULE);
        if (!BusinessSmsAccount.RECEIPT_PAPER.equals(mode) && !BusinessSmsAccount.RECEIPT_SMS.equals(mode)
                && !BusinessSmsAccount.RECEIPT_BOTH.equals(mode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose paper, SMS, or both.");
        }
        tx.executeWithoutResult(status -> {
            BusinessSmsAccount account = loadOrCreateAccount(businessId);
            account.setReceiptMode(mode);
            accountRepository.save(account);
        });
        return getAccount();
    }

    public List<SmsMessageResponse> history() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, MODULE);
        return messageRepository.findTop50ByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(SmsMessageResponse::from)
                .toList();
    }

    public List<SmsLedgerResponse> creditHistory() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, MODULE);
        return ledgerRepository.findTop50ByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(SmsLedgerResponse::from)
                .toList();
    }

    public SmsSegmentCalculator.Result estimate(String message) {
        moduleAccessService.requireModule(TenantContext.getBusinessId(), MODULE);
        return SmsSegmentCalculator.calculate(message);
    }

    // ---- Sending ----

    // Sends one real message to one number from the business's own account and
    // sender name, charging its credits — the Super-Admin-free way for an owner
    // to see their sender name and balance working end to end.
    public SmsMessageResponse sendTest(SmsSendTestRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        SmsMessage sent = send(businessId, req.phone(), req.message(), SmsMessage.KIND_TEST, null);
        if (SmsMessage.FAILED.equals(sent.getStatus())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "That message couldn't be sent, and you haven't been charged. Please try again — "
                            + "if it keeps happening, contact support.");
        }
        return SmsMessageResponse.from(sent);
    }

    /**
     * Sends one message and returns its final row (status SENT or FAILED). Throws
     * only for problems the CALLER can fix BEFORE anything is charged — no
     * module, bad number, unapproved sender name, not enough credits. A
     * provider-side failure is not thrown: it comes back as a FAILED row with
     * the credits already refunded, so callers that send in bulk later can carry on.
     */
    public SmsMessage send(UUID businessId, String rawPhone, String text, String kind, UUID customerId) {
        return send(businessId, rawPhone, text, kind, customerId, null, null);
    }

    /**
     * As above, tagged with what the message is about. For a receipt the unique
     * index on (source) means a second live receipt for the same sale makes the
     * reservation transaction fail — credits and all rolled back — so a sale can
     * never be charged for twice.
     */
    public SmsMessage send(UUID businessId, String rawPhone, String text, String kind, UUID customerId,
                           String sourceType, UUID sourceId) {
        moduleAccessService.requireModule(businessId, MODULE);

        if (!gateway.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS isn't available right now. Please contact support.");
        }

        String message = text == null ? "" : text.trim();
        if (message.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Write a message first.");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That message is too long.");
        }
        if (!PhoneUtils.isValid(rawPhone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid phone number.");
        }
        String recipient = PhoneUtils.normalize(rawPhone);

        SmsSegmentCalculator.Result size = SmsSegmentCalculator.calculate(message);
        int credits = size.segments();

        // Step 1 — reserve credits and record the message, atomically.
        Reservation reservation = tx.execute(status -> {
            BusinessSmsAccount account = loadOrCreateAccount(businessId);
            if (!account.senderApproved()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Your SMS sender name isn't approved yet. Contact support to get it set up.");
            }
            Integer balanceAfter = accountRepository.debit(businessId, credits);
            if (balanceAfter == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Not enough SMS credits — this message needs " + credits + " credit" + (credits == 1 ? "" : "s")
                                + ". Buy more to continue.");
            }
            SmsMessage queued = messageRepository.save(SmsMessage.builder()
                    .businessId(businessId)
                    .customerId(customerId)
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .recipient(recipient)
                    .message(message)
                    .senderId(account.getSenderId())
                    .kind(kind)
                    .segments(size.segments())
                    .creditsUsed(credits)
                    .status(SmsMessage.QUEUED)
                    .build());
            writeLedger(businessId, -credits, balanceAfter, SmsCreditLedger.SEND, queued.getId().toString(),
                    "SMS to " + recipient, null);
            return new Reservation(queued.getId(), account.getSenderId(), balanceAfter, account.getLowBalanceThreshold());
        });

        // Step 2 — the provider call, deliberately outside any transaction.
        SmsGateway.SendResult result;
        try {
            result = gateway.send(reservation.senderId(), recipient, message);
        } catch (RuntimeException e) {
            log.error("SMS gateway threw ({})", e.getClass().getSimpleName());
            result = SmsGateway.SendResult.rejected("Unexpected provider error.");
        }

        // Step 3 — settle: mark it sent, or refund and mark it failed.
        SmsGateway.SendResult finalResult = result;
        return tx.execute(status -> {
            SmsMessage message1 = messageRepository.findById(reservation.messageId()).orElseThrow();
            if (finalResult.accepted()) {
                message1.setStatus(SmsMessage.SENT);
                message1.setProviderRef(truncate(finalResult.providerRef(), 100));
                SmsMessage saved = messageRepository.save(message1);
                alertIfLow(businessId, reservation.balanceAfter() + credits, reservation.balanceAfter(), reservation.threshold());
                return saved;
            }
            message1.setStatus(SmsMessage.FAILED);
            message1.setError(truncate(finalResult.error(), 500));
            SmsMessage saved = messageRepository.save(message1);
            Integer refunded = accountRepository.credit(businessId, credits);
            writeLedger(businessId, credits, refunded, SmsCreditLedger.REFUND, saved.getId().toString(),
                    "SMS to " + recipient + " couldn't be sent", null);
            return saved;
        });
    }

    // ---- Shared helpers (also used by SmsCreditService / SmsPlatformService) ----

    /** Account row for a business, created on first use. Safe to call from any thread, any number of times. */
    BusinessSmsAccount ensureAccount(UUID businessId) {
        return tx.execute(status -> loadOrCreateAccount(businessId));
    }

    private BusinessSmsAccount loadOrCreateAccount(UUID businessId) {
        accountRepository.createIfMissing(businessId);
        return accountRepository.findById(businessId).orElseThrow();
    }

    int balanceOf(UUID businessId) {
        Integer balance = accountRepository.balanceOf(businessId);
        return balance == null ? 0 : balance;
    }

    void writeLedger(UUID businessId, int delta, int balanceAfter, String type, String reference, String description, UUID createdBy) {
        ledgerRepository.save(SmsCreditLedger.builder()
                .businessId(businessId)
                .delta(delta)
                .balanceAfter(balanceAfter)
                .type(type)
                .reference(reference)
                .description(description)
                .createdBy(createdBy)
                .build());
    }

    // Once, when a send is what takes the balance from above the threshold to at or
    // below it — not on every send while it stays low.
    private void alertIfLow(UUID businessId, int balanceBefore, int balanceAfter, int threshold) {
        if (balanceBefore > threshold && balanceAfter <= threshold) {
            notificationService.create(businessId, "SMS_LOW_BALANCE", "SMS credits running low",
                    "You have " + balanceAfter + " SMS credit" + (balanceAfter == 1 ? "" : "s")
                            + " left. Buy more so your customer messages keep going out.",
                    "SMS_ACCOUNT", businessId);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record Reservation(UUID messageId, String senderId, int balanceAfter, int threshold) {
    }
}
