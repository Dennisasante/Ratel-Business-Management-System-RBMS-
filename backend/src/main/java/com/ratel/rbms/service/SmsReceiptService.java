package com.ratel.rbms.service;

import com.ratel.rbms.dto.SmsReceiptPreviewResponse;
import com.ratel.rbms.dto.SmsReceiptStatusResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessSmsAccount;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.SaleItem;
import com.ratel.rbms.entity.SmsMessage;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.SaleItemRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.repository.SmsMessageRepository;
import com.ratel.rbms.sms.SmsGateway;
import com.ratel.rbms.sms.SmsReceiptComposer;
import com.ratel.rbms.sms.SmsSegmentCalculator;
import com.ratel.rbms.tenant.TenantContext;
import com.ratel.rbms.util.PhoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The short text receipt a customer gets when a sale is paid. All the money
 * rules (credits reserved first, refunded if the provider fails) live in
 * SmsService.send(); this decides WHETHER and WHAT to send, and guarantees a
 * sale is texted at most once.
 *
 * A receipt never blocks or undoes a sale: every "can't send" outcome is a
 * status the cashier can read, not an exception.
 */
@Service
public class SmsReceiptService {

    private static final Logger log = LoggerFactory.getLogger(SmsReceiptService.class);

    static final String SOURCE_SALE = "SALE";

    // Status codes the cashier's screen understands.
    public static final String OFF = "OFF";                  // this business doesn't send SMS receipts
    public static final String UNPAID = "UNPAID";            // not fully paid yet — the receipt goes out when it is
    public static final String NO_CUSTOMER = "NO_CUSTOMER";  // a walk-in: nobody to text
    public static final String NO_PHONE = "NO_PHONE";        // the customer has no usable phone number
    public static final String NOT_READY = "NOT_READY";      // sender name not approved / provider down
    public static final String NO_CREDITS = "NO_CREDITS";    // out of credits
    public static final String PENDING = "PENDING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final BusinessRepository businessRepository;
    private final CustomerRepository customerRepository;
    private final SmsMessageRepository messageRepository;
    private final SmsService smsService;
    private final ModuleAccessService moduleAccessService;
    private final LoyaltyService loyaltyService;
    private final SmsGateway gateway;
    private final String frontendUrl;

    public SmsReceiptService(
            SaleRepository saleRepository,
            SaleItemRepository saleItemRepository,
            BusinessRepository businessRepository,
            CustomerRepository customerRepository,
            SmsMessageRepository messageRepository,
            SmsService smsService,
            ModuleAccessService moduleAccessService,
            LoyaltyService loyaltyService,
            SmsGateway gateway,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.businessRepository = businessRepository;
        this.customerRepository = customerRepository;
        this.messageRepository = messageRepository;
        this.smsService = smsService;
        this.moduleAccessService = moduleAccessService;
        this.loyaltyService = loyaltyService;
        this.gateway = gateway;
        this.frontendUrl = frontendUrl;
    }

    /** Called when a sale becomes paid — only sends if the business has SMS receipts switched on. */
    public SmsReceiptStatusResponse sendAutomatically(UUID saleId, UUID businessId) {
        return attempt(saleId, businessId, false);
    }

    /** The cashier's "Send SMS receipt" button — works whatever the business's receipt mode is. */
    public SmsReceiptStatusResponse sendManually(UUID saleId) {
        return attempt(saleId, TenantContext.getBusinessId(), true);
    }

    /** What happened (or would happen) for this sale's receipt, for the screen after "complete order". */
    public SmsReceiptStatusResponse statusFor(UUID saleId) {
        UUID businessId = TenantContext.getBusinessId();
        Sale sale = requireSale(saleId, businessId);
        if (!moduleAccessService.hasModule(businessId, SmsService.MODULE)) {
            return new SmsReceiptStatusResponse(OFF, "SMS isn't switched on for this business.", false);
        }
        BusinessSmsAccount account = smsService.ensureAccount(businessId);

        Optional<SmsMessage> existing = liveReceipt(saleId);
        if (existing.isPresent()) {
            return fromMessage(existing.get());
        }
        Optional<SmsReceiptStatusResponse> blocker = blocker(sale, account, true);
        if (blocker.isPresent()) {
            return blocker.get();
        }
        // Sendable. If the business doesn't auto-send, say so, but offer the button.
        return account.sendsSmsReceipts()
                ? new SmsReceiptStatusResponse(PENDING, "The SMS receipt is being sent.", true)
                : new SmsReceiptStatusResponse(OFF, "This business doesn't send SMS receipts automatically.", true);
    }

    /** What the receipt text looks like for this business — shown on the settings screen. */
    public SmsReceiptPreviewResponse preview() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, SmsService.MODULE);
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        // With a points program on, the sample shows the points line and the personal link, as real receipts will.
        boolean points = loyaltyService.isActive(businessId);
        String text = SmsReceiptComposer.compose(new SmsReceiptComposer.Input(
                business.getName(), "Ama", 1042, business.getCurrency(),
                new BigDecimal("770.00"), new BigDecimal("20.00"),
                points ? "You currently have 7 points (reach 10 points to earn a reward)" : null,
                points ? frontendHost() + "/start/" + business.getSlug() : bookingLink(business),
                points ? pointsLinkLabel(business) : null));
        SmsSegmentCalculator.Result size = SmsSegmentCalculator.calculate(text);
        return new SmsReceiptPreviewResponse(text, size.segments(), size.length(), size.unicode());
    }

    // ---- internals ----

    private SmsReceiptStatusResponse attempt(UUID saleId, UUID businessId, boolean manual) {
        Sale sale = requireSale(saleId, businessId);
        if (!moduleAccessService.hasModule(businessId, SmsService.MODULE)) {
            return new SmsReceiptStatusResponse(OFF, "SMS isn't switched on for this business.", false);
        }
        BusinessSmsAccount account = smsService.ensureAccount(businessId);
        if (!manual && !account.sendsSmsReceipts()) {
            return new SmsReceiptStatusResponse(OFF, "This business doesn't send SMS receipts automatically.", false);
        }

        Optional<SmsMessage> existing = liveReceipt(saleId);
        if (existing.isPresent()) {
            return fromMessage(existing.get());
        }
        Optional<SmsReceiptStatusResponse> blocker = blocker(sale, account, manual);
        if (blocker.isPresent()) {
            return blocker.get();
        }

        Business business = businessRepository.findById(businessId).orElseThrow();
        Customer customer = customerRepository.findById(sale.getCustomerId()).orElseThrow();
        // Count today's visit BEFORE writing the text, so the points shown already include this sale.
        // Idempotent — the loyalty listener may well have done it already.
        try {
            loyaltyService.recordVisitForSale(saleId);
        } catch (RuntimeException e) {
            log.warn("Couldn't count the loyalty visit for sale {} ({})", saleId, e.getClass().getSimpleName());
        }
        String text = receiptText(sale, business, customer);

        try {
            SmsMessage sent = smsService.send(businessId, customer.getPhone(), text, SmsMessage.KIND_RECEIPT,
                    customer.getId(), SOURCE_SALE, saleId);
            return fromMessage(sent);
        } catch (ApiException e) {
            // Nothing was charged. Most likely the credits ran out between the check and the send.
            return smsService.balanceOf(businessId) < SmsSegmentCalculator.calculate(text).segments()
                    ? noCredits()
                    : new SmsReceiptStatusResponse(NOT_READY, e.getMessage(), true);
        } catch (DataAccessException e) {
            // Two paths raced to text the same sale; the unique index let exactly one through
            // and rolled the other's credits back. Whoever won has the live receipt.
            return liveReceipt(saleId).map(this::fromMessage)
                    .orElseGet(() -> new SmsReceiptStatusResponse(FAILED, "The SMS receipt couldn't be sent.", true));
        }
    }

    /** Why this sale can't be texted right now, or empty if it can. */
    private Optional<SmsReceiptStatusResponse> blocker(Sale sale, BusinessSmsAccount account, boolean manual) {
        if (!"PAID".equals(sale.getPaymentStatus())) {
            return Optional.of(new SmsReceiptStatusResponse(UNPAID, "The receipt is sent once the sale is fully paid.", false));
        }
        if (sale.getCustomerId() == null) {
            return Optional.of(new SmsReceiptStatusResponse(NO_CUSTOMER, "Walk-in sale — add a customer with a phone number to text them a receipt.", false));
        }
        Customer customer = customerRepository.findById(sale.getCustomerId()).orElse(null);
        if (customer == null || !PhoneUtils.isValid(customer.getPhone())) {
            return Optional.of(new SmsReceiptStatusResponse(NO_PHONE, "This customer has no phone number — add one to text them a receipt.", false));
        }
        if (!gateway.isConfigured() || !account.senderApproved()) {
            return Optional.of(new SmsReceiptStatusResponse(NOT_READY, "SMS receipts aren't ready yet — your sender name isn't approved.", false));
        }
        Business business = businessRepository.findById(sale.getBusinessId()).orElseThrow();
        String text = receiptText(sale, business, customer);
        if (smsService.balanceOf(sale.getBusinessId()) < SmsSegmentCalculator.calculate(text).segments()) {
            return Optional.of(noCredits());
        }
        return Optional.empty();
    }

    private SmsReceiptStatusResponse noCredits() {
        return new SmsReceiptStatusResponse(NO_CREDITS, "Out of SMS credits — buy more to text receipts. The sale is unaffected.", true);
    }

    private SmsReceiptStatusResponse fromMessage(SmsMessage m) {
        return switch (m.getStatus()) {
            case SmsMessage.SENT -> new SmsReceiptStatusResponse(SENT, "SMS receipt sent to " + m.getRecipient() + ".", false);
            case SmsMessage.QUEUED -> new SmsReceiptStatusResponse(PENDING, "The SMS receipt is being sent.", false);
            default -> new SmsReceiptStatusResponse(FAILED, "The SMS receipt couldn't be sent — you weren't charged for it.", true);
        };
    }

    // A FAILED receipt doesn't count as "already sent": the cashier can retry it.
    private Optional<SmsMessage> liveReceipt(UUID saleId) {
        return messageRepository.findFirstBySourceIdAndKindOrderByCreatedAtDesc(saleId, SmsMessage.KIND_RECEIPT)
                .filter(m -> !SmsMessage.FAILED.equals(m.getStatus()));
    }

    private Sale requireSale(UUID saleId, UUID businessId) {
        return saleRepository.findById(saleId)
                .filter(s -> s.getBusinessId().equals(businessId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sale not found."));
    }

    private BigDecimal totalSaved(Sale sale) {
        List<SaleItem> items = saleItemRepository.findAllBySaleId(sale.getId());
        return items.stream().map(SaleItem::getDiscountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String firstName(Customer customer) {
        String name = customer.getFullName() == null ? "" : customer.getFullName().trim();
        return name.isEmpty() ? null : name.split("\\s+")[0];
    }

    // The compact receipt text. With an active points program it carries the customer's points and
    // their personal link (which leads to their points and a Book button); otherwise the booking link.
    private String receiptText(Sale sale, Business business, Customer customer) {
        String pointsLine = loyaltyService.receiptLine(business.getId(), customer.getId());
        String pointsLink = loyaltyService.pointsPageLink(business.getId());
        boolean usePoints = pointsLine != null && pointsLink != null;
        return SmsReceiptComposer.compose(new SmsReceiptComposer.Input(
                business.getName(), firstName(customer), sale.getSaleNumber(), business.getCurrency(),
                sale.getTotalAmount(), totalSaved(sale),
                usePoints ? pointsLine : null,
                usePoints ? pointsLink : bookingLink(business),
                usePoints ? pointsLinkLabel(business) : null));
    }

    // The personal link leads to the customer's points and, when the business takes online
    // bookings, a Book button — so the line only promises what the page really offers.
    private String pointsLinkLabel(Business business) {
        return moduleAccessService.hasModule(business.getId(), "BOOKINGS")
                ? "To book an appointment or check your points:"
                : "To check your points:";
    }

    private String frontendHost() {
        return frontendUrl.replaceFirst("^https?://", "").replaceFirst("/+$", "");
    }

    // Written without "https://" to save 8 characters — phones still make it tappable.
    // Only included when the business actually has the booking page switched on.
    private String bookingLink(Business business) {
        if (!moduleAccessService.hasModule(business.getId(), "BOOKINGS")) {
            return null;
        }
        return frontendHost() + "/book/" + business.getSlug();
    }
}
