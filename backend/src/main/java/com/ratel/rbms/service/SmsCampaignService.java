package com.ratel.rbms.service;

import com.ratel.rbms.dto.SmsAudienceRowResponse;
import com.ratel.rbms.dto.SmsCampaignDetailResponse;
import com.ratel.rbms.dto.SmsCampaignPreviewResponse;
import com.ratel.rbms.dto.SmsCampaignRequest;
import com.ratel.rbms.dto.SmsCampaignSummaryResponse;
import com.ratel.rbms.entity.BusinessSmsAccount;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.SmsCampaign;
import com.ratel.rbms.entity.SmsCampaignRecipient;
import com.ratel.rbms.entity.SmsCreditLedger;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.SmsAccountRepository;
import com.ratel.rbms.repository.SmsCampaignRecipientRepository;
import com.ratel.rbms.repository.SmsCampaignRepository;
import com.ratel.rbms.sms.SmsGateway;
import com.ratel.rbms.sms.SmsSegmentCalculator;
import com.ratel.rbms.tenant.TenantContext;
import com.ratel.rbms.util.PhoneUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A promotional text to many customers.
 *
 * The money rules mirror single sends, scaled up:
 *  - everything the campaign will cost is taken in ONE atomic debit before anything is sent, so a
 *    business can never start a campaign it can't pay for, even with campaigns racing each other;
 *  - texts that couldn't be sent (or were cancelled before sending) are handed back, exactly once,
 *    under the campaign's row lock;
 *  - the same request key can only ever create one campaign (a double-click is harmless).
 */
@Service
public class SmsCampaignService {

    static final int MAX_RECIPIENTS = 5000;
    static final String OPT_OUT_FOOTER = "\nReply STOP to opt out.";

    private final SmsCampaignRepository campaignRepository;
    private final SmsCampaignRecipientRepository recipientRepository;
    private final CustomerRepository customerRepository;
    private final SmsAccountRepository accountRepository;
    private final SmsService smsService;
    private final ModuleAccessService moduleAccessService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final SmsGateway gateway;
    private final SmsCampaignSender sender;
    private final TransactionTemplate tx;

    public SmsCampaignService(
            SmsCampaignRepository campaignRepository,
            SmsCampaignRecipientRepository recipientRepository,
            CustomerRepository customerRepository,
            SmsAccountRepository accountRepository,
            SmsService smsService,
            ModuleAccessService moduleAccessService,
            NotificationService notificationService,
            ActivityLogService activityLogService,
            SmsGateway gateway,
            SmsCampaignSender sender,
            PlatformTransactionManager transactionManager
    ) {
        this.campaignRepository = campaignRepository;
        this.recipientRepository = recipientRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.smsService = smsService;
        this.moduleAccessService = moduleAccessService;
        this.notificationService = notificationService;
        this.activityLogService = activityLogService;
        this.gateway = gateway;
        this.sender = sender;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ---- The picker ----

    public List<SmsAudienceRowResponse> audience() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, SmsService.MODULE);
        return customerRepository.findAllByBusinessIdOrderByFullNameAsc(businessId).stream()
                .map(c -> new SmsAudienceRowResponse(c.getId(), c.getFullName(), c.getPhone(), c.isSmsOptOut(),
                        PhoneUtils.isValid(c.getPhone()) && !c.isSmsOptOut()))
                .toList();
    }

    // ---- Preview: who, what it costs, what it will look like — before anything is spent ----

    public SmsCampaignPreviewResponse preview(SmsCampaignRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, SmsService.MODULE);
        Built built = build(businessId, req);
        int available = smsService.balanceOf(businessId);
        int min = built.recipients.stream().mapToInt(r -> r.segments).min().orElse(0);
        int max = built.recipients.stream().mapToInt(r -> r.segments).max().orElse(0);
        String sample = built.recipients.isEmpty() ? null : built.recipients.get(0).message;
        return new SmsCampaignPreviewResponse(built.recipients.size(), built.skippedNoPhone, built.skippedOptedOut,
                built.skippedDuplicate, built.totalCredits, available, available >= built.totalCredits, min, max, sample);
    }

    // ---- Send: reserve the credits and queue every text, atomically ----

    public SmsCampaignSummaryResponse create(SmsCampaignRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        UUID userId = TenantContext.getUserId();
        moduleAccessService.requireModule(businessId, SmsService.MODULE);
        if (req.requestKey() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Something went wrong preparing this send. Please try again.");
        }
        if (!gateway.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS isn't available right now. Please contact support.");
        }

        // The same request sent again (double-click, retry after a dropped connection) just returns the campaign it made.
        var existing = campaignRepository.findByBusinessIdAndRequestKey(businessId, req.requestKey());
        if (existing.isPresent()) {
            return summary(existing.get());
        }

        Built built = build(businessId, req);
        if (built.recipients.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "There's nobody to send this to — customers need a phone number and must not have opted out.");
        }

        SmsCampaign campaign;
        try {
            campaign = tx.execute(status -> {
                BusinessSmsAccount account = smsService.ensureAccount(businessId);
                if (!account.senderApproved()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Your SMS sender name isn't approved yet. Contact support to get it set up.");
                }
                // The one debit for the whole campaign. null = not enough, and nothing has changed.
                Integer balanceAfter = accountRepository.debit(businessId, built.totalCredits);
                if (balanceAfter == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Not enough SMS credits — this campaign needs "
                            + built.totalCredits + " and you have " + smsService.balanceOf(businessId) + ". Buy more to continue.");
                }
                SmsCampaign saved = campaignRepository.save(SmsCampaign.builder()
                        .businessId(businessId)
                        .name(blankToNull(req.name()))
                        .messageTemplate(req.message().trim())
                        .audienceMode(req.audience())
                        .includeFooter(built.footer)
                        .recipientsCount(built.recipients.size())
                        .creditsReserved(built.totalCredits)
                        .requestKey(req.requestKey())
                        .createdBy(userId)
                        .build());
                for (Prepared p : built.recipients) {
                    recipientRepository.save(SmsCampaignRecipient.builder()
                            .campaignId(saved.getId()).businessId(businessId).customerId(p.customerId)
                            .phone(p.phone).message(p.message).segments(p.segments).build());
                }
                smsService.writeLedger(businessId, -built.totalCredits, balanceAfter, SmsCreditLedger.CAMPAIGN,
                        saved.getId().toString(), "Campaign" + (saved.getName() == null ? "" : " \"" + saved.getName() + "\"")
                                + " to " + built.recipients.size() + " customers", userId);
                return saved;
            });
        } catch (DataIntegrityViolationException raced) {
            // Two identical requests at the same instant: the unique key let one through and rolled the other (and its debit) back.
            return campaignRepository.findByBusinessIdAndRequestKey(businessId, req.requestKey()).map(this::summary).orElseThrow(() -> raced);
        }

        activityLogService.log("Started an SMS campaign to " + built.recipients.size() + " customers", "SMS_CAMPAIGN", campaign.getId());
        sender.kick();
        return summary(campaign);
    }

    // ---- Reports ----

    public List<SmsCampaignSummaryResponse> list() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, SmsService.MODULE);
        return campaignRepository.findTop50ByBusinessIdOrderByCreatedAtDesc(businessId).stream().map(this::summary).toList();
    }

    public SmsCampaignDetailResponse detail(UUID campaignId) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, SmsService.MODULE);
        SmsCampaign campaign = campaignRepository.findByIdAndBusinessId(campaignId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campaign not found."));
        List<SmsCampaignRecipient> recipients = recipientRepository.findTop500ByCampaignIdOrderByPhoneAsc(campaignId);
        Map<UUID, String> names = customerRepository.findAllById(
                        recipients.stream().map(SmsCampaignRecipient::getCustomerId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Customer::getId, Customer::getFullName));
        return new SmsCampaignDetailResponse(summary(campaign), recipients.stream()
                .map(r -> new SmsCampaignDetailResponse.Recipient(r.getCustomerId(), names.get(r.getCustomerId()), r.getPhone(),
                        r.getStatus(), r.getError()))
                .toList());
    }

    // ---- Stop it ----

    /** Stops the texts that haven't gone out yet and hands their credits back. Texts already sent stay sent. */
    public SmsCampaignSummaryResponse cancel(UUID campaignId) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, SmsService.MODULE);
        SmsCampaign campaign = campaignRepository.findByIdAndBusinessId(campaignId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campaign not found."));

        tx.executeWithoutResult(status -> {
            SmsCampaign locked = campaignRepository.findByIdForUpdate(campaign.getId()).orElseThrow();
            if (!SmsCampaign.SENDING.equals(locked.getStatus())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "This campaign has already finished.");
            }
            locked.setStatus(SmsCampaign.CANCELLED);
            campaignRepository.save(locked);
            recipientRepository.cancelPending(locked.getId());
        });
        activityLogService.log("Cancelled an SMS campaign", "SMS_CAMPAIGN", campaign.getId());
        settle(campaign.getId());
        return summary(campaignRepository.findById(campaign.getId()).orElseThrow());
    }

    // ---- Settling: hand back what wasn't sent, and finish the campaign when nothing is left to do ----

    /**
     * Safe to call any number of times, from anywhere, at the same time. Under the campaign's row lock it works
     * out how many credits belong back to the business (texts that failed or were cancelled), returns only
     * the part not already returned, and marks the campaign finished once nothing is still waiting or sending.
     */
    public void settle(UUID campaignId) {
        Boolean justFinished = tx.execute(status -> {
            SmsCampaign c = campaignRepository.findByIdForUpdate(campaignId).orElse(null);
            if (c == null) {
                return false;
            }
            long refundable = recipientRepository.sumSegments(campaignId, List.of(SmsCampaignRecipient.FAILED, SmsCampaignRecipient.CANCELLED));
            int delta = (int) refundable - c.getCreditsRefunded();
            if (delta > 0) {
                Integer balance = accountRepository.credit(c.getBusinessId(), delta);
                smsService.writeLedger(c.getBusinessId(), delta, balance, SmsCreditLedger.REFUND, c.getId().toString(),
                        "Campaign" + (c.getName() == null ? "" : " \"" + c.getName() + "\"") + ": " + delta
                                + " credit" + (delta == 1 ? "" : "s") + " returned for texts that weren't sent", null);
                c.setCreditsRefunded((int) refundable);
            }
            boolean finished = false;
            if (c.getFinishedAt() == null && recipientRepository.countOpen(campaignId) == 0) {
                c.setFinishedAt(Instant.now());
                if (SmsCampaign.SENDING.equals(c.getStatus())) {
                    c.setStatus(SmsCampaign.DONE);
                }
                finished = true;
            }
            campaignRepository.save(c);
            return finished;
        });
        if (Boolean.TRUE.equals(justFinished)) {
            SmsCampaign c = campaignRepository.findById(campaignId).orElseThrow();
            SmsCampaignSummaryResponse s = summary(c);
            notificationService.create(c.getBusinessId(), "SMS_CAMPAIGN_DONE",
                    SmsCampaign.CANCELLED.equals(c.getStatus()) ? "SMS campaign stopped" : "SMS campaign finished",
                    s.sent() + " sent" + (s.failed() > 0 ? ", " + s.failed() + " couldn't be sent (credits returned)" : "")
                            + (s.cancelled() > 0 ? ", " + s.cancelled() + " cancelled" : "") + ".",
                    "SMS_CAMPAIGN", c.getId());
        }
    }

    // ---- Building the audience and each customer's own text ----

    private record Prepared(UUID customerId, String phone, String message, int segments) {
    }

    private static final class Built {
        final List<Prepared> recipients = new ArrayList<>();
        int skippedNoPhone;
        int skippedOptedOut;
        int skippedDuplicate;
        int totalCredits;
        boolean footer;
    }

    private Built build(UUID businessId, SmsCampaignRequest req) {
        String template = req.message() == null ? "" : req.message().trim();
        if (template.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Write the message first.");
        }
        boolean footer = req.includeOptOutFooter() == null || req.includeOptOutFooter();

        List<Customer> customers;
        if (SmsCampaign.MODE_SELECTED.equals(req.audience())) {
            if (req.customerIds() == null || req.customerIds().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Pick at least one customer.");
            }
            // Only this business's own customers, whatever ids were sent.
            Set<UUID> wanted = new HashSet<>(req.customerIds());
            customers = customerRepository.findAllByBusinessIdOrderByFullNameAsc(businessId).stream()
                    .filter(c -> wanted.contains(c.getId())).toList();
        } else {
            customers = customerRepository.findAllByBusinessIdOrderByFullNameAsc(businessId);
        }

        Built built = new Built();
        built.footer = footer;
        Set<String> seenPhones = new HashSet<>();
        for (Customer c : customers) {
            if (c.isSmsOptOut()) {
                built.skippedOptedOut++;
                continue;
            }
            if (!PhoneUtils.isValid(c.getPhone())) {
                built.skippedNoPhone++;
                continue;
            }
            String phone = PhoneUtils.normalize(c.getPhone());
            // Two customers sharing a number (a family) get one text, not two.
            if (!seenPhones.add(phone)) {
                built.skippedDuplicate++;
                continue;
            }
            String message = render(template, c.getFullName(), footer);
            int segments = SmsSegmentCalculator.calculate(message).segments();
            built.recipients.add(new Prepared(c.getId(), phone, message, segments));
            built.totalCredits += segments;
        }
        if (built.recipients.size() > MAX_RECIPIENTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A campaign can go to at most " + MAX_RECIPIENTS + " customers at once.");
        }
        return built;
    }

    /** The template with {name} filled in (first name, or "there" if they have none) and the opt-out line added. */
    static String render(String template, String fullName, boolean footer) {
        String first = fullName == null ? "" : fullName.trim().split("\\s+")[0];
        String text = template.replace("{name}", first.isEmpty() ? "there" : first);
        return footer ? text + OPT_OUT_FOOTER : text;
    }

    private SmsCampaignSummaryResponse summary(SmsCampaign c) {
        Map<String, Long> counts = new HashMap<>();
        for (var row : recipientRepository.countsByStatus(c.getId())) {
            counts.put(row.getStatus(), row.getCount());
        }
        int sent = counts.getOrDefault(SmsCampaignRecipient.SENT, 0L).intValue();
        int failed = counts.getOrDefault(SmsCampaignRecipient.FAILED, 0L).intValue();
        int cancelled = counts.getOrDefault(SmsCampaignRecipient.CANCELLED, 0L).intValue();
        int waiting = counts.getOrDefault(SmsCampaignRecipient.PENDING, 0L).intValue()
                + counts.getOrDefault(SmsCampaignRecipient.SENDING, 0L).intValue();
        return new SmsCampaignSummaryResponse(c.getId(), c.getName(), c.getStatus(), c.getMessageTemplate(), c.getRecipientsCount(),
                sent, failed, waiting, cancelled, c.getCreditsReserved(), c.getCreditsRefunded(),
                c.getCreditsReserved() - c.getCreditsRefunded(), c.getCreatedAt(), c.getFinishedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
