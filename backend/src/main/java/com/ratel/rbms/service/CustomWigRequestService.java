package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.ratel.rbms.dto.CreateStaffCustomWigRequestRequest;
import com.ratel.rbms.dto.CustomItemAttributeResponse;
import com.ratel.rbms.dto.CustomWigRequestCreatedResponse;
import com.ratel.rbms.dto.CustomWigRequestDetailResponse;
import com.ratel.rbms.dto.CustomWigRequestResponse;
import com.ratel.rbms.dto.CustomWigSelectionInput;
import com.ratel.rbms.dto.CustomWigSelectionResponse;
import com.ratel.rbms.dto.MobileMoneyChargeRequest;
import com.ratel.rbms.dto.MobileMoneyChargeResponse;
import com.ratel.rbms.dto.PublicCustomWigConfigResponse;
import com.ratel.rbms.dto.RecordPaymentRequest;
import com.ratel.rbms.dto.RefundRequest;
import com.ratel.rbms.dto.SubmitCustomWigRequestRequest;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessIntegrations;
import com.ratel.rbms.entity.CustomItemAttribute;
import com.ratel.rbms.entity.CustomItemAttributeOption;
import com.ratel.rbms.entity.CustomWigRequest;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessIntegrationsRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomItemAttributeOptionRepository;
import com.ratel.rbms.repository.CustomItemAttributeRepository;
import com.ratel.rbms.repository.CustomWigRequestRepository;
import com.ratel.rbms.repository.PaymentTransactionRepository;
import com.ratel.rbms.security.RateLimiterService;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Both sides of the custom wig configurator: public submission (no JWT,
 * businessId passed explicitly — same reasoning as BookingService) and
 * owner-side review/quote/decline (TenantContext-scoped). Kept in one class
 * since both sides share the same attribute/option pricing lookups.
 */
@Service
public class CustomWigRequestService {

    private static final Set<String> ALLOWED_PHOTO_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final CustomWigRequestRepository customWigRequestRepository;
    private final CustomItemAttributeRepository attributeRepository;
    private final CustomItemAttributeOptionRepository optionRepository;
    private final BusinessRepository businessRepository;
    private final BusinessIntegrationsRepository businessIntegrationsRepository;
    private final PlanFeatureService planFeatureService;
    private final RateLimiterService rateLimiterService;
    private final EmailService emailService;
    private final WhatsAppLinkService whatsAppLinkService;
    private final ActivityLogService activityLogService;
    private final ObjectMapper objectMapper;
    private final String uploadDir;
    private final PaymentTransactionService paymentTransactionService;
    private final PaystackService paystackService;
    private final NotificationService notificationService;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public CustomWigRequestService(
            CustomWigRequestRepository customWigRequestRepository,
            CustomItemAttributeRepository attributeRepository,
            CustomItemAttributeOptionRepository optionRepository,
            BusinessRepository businessRepository,
            BusinessIntegrationsRepository businessIntegrationsRepository,
            PlanFeatureService planFeatureService,
            RateLimiterService rateLimiterService,
            EmailService emailService,
            WhatsAppLinkService whatsAppLinkService,
            ActivityLogService activityLogService,
            ObjectMapper objectMapper,
            @Value("${app.upload-dir}") String uploadDir,
            PaymentTransactionService paymentTransactionService,
            PaystackService paystackService,
            NotificationService notificationService,
            PaymentTransactionRepository paymentTransactionRepository
    ) {
        this.customWigRequestRepository = customWigRequestRepository;
        this.attributeRepository = attributeRepository;
        this.optionRepository = optionRepository;
        this.businessRepository = businessRepository;
        this.businessIntegrationsRepository = businessIntegrationsRepository;
        this.planFeatureService = planFeatureService;
        this.rateLimiterService = rateLimiterService;
        this.emailService = emailService;
        this.whatsAppLinkService = whatsAppLinkService;
        this.activityLogService = activityLogService;
        this.objectMapper = objectMapper;
        this.uploadDir = uploadDir;
        this.paymentTransactionService = paymentTransactionService;
        this.paystackService = paystackService;
        this.notificationService = notificationService;
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    // ---- Public side ----

    public PublicCustomWigConfigResponse getConfig(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        return buildConfig(business);
    }

    // Backs the hosted custom-order page (ratel.app/order/{slug}) for businesses
    // with no website of their own to embed the widget on — same reasoning as
    // BookingService.getWidgetConfigBySlug.
    public PublicCustomWigConfigResponse getConfigBySlug(String slug) {
        Business business = businessRepository.findBySlug(slug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        return buildConfig(business);
    }

    private PublicCustomWigConfigResponse buildConfig(Business business) {
        UUID businessId = business.getId();
        boolean enabled = planFeatureService.hasFeature(businessId, PlanFeature.CUSTOM_WIG_REQUESTS);
        List<CustomItemAttributeResponse> attributes = enabled
                ? attributeRepository.findAllByBusinessIdOrderBySortOrderAsc(businessId).stream()
                        .map(this::toAttributeResponse)
                        .toList()
                : List.of();
        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(businessId).orElse(null);
        String businessWhatsappLink = integrations != null
                ? whatsAppLinkService.buildLink(integrations.getWhatsappNotifyNumber(),
                        "Hi " + business.getName() + ", I have a question about a custom wig request.")
                : null;
        return new PublicCustomWigConfigResponse(businessId, business.getName(), enabled, business.getCurrency(), attributes, businessWhatsappLink);
    }

    @Transactional
    public CustomWigRequestCreatedResponse submit(UUID businessId, SubmitCustomWigRequestRequest req, MultipartFile photo) {
        rateLimiterService.checkAllowed("public-wig-request:" + businessId, 10, Duration.ofMinutes(15));
        rateLimiterService.recordAttempt("public-wig-request:" + businessId);

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request isn't available."));
        if (!planFeatureService.hasFeature(businessId, PlanFeature.CUSTOM_WIG_REQUESTS)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Custom requests aren't available for this business.");
        }

        if (req.customerName() == null || req.customerName().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your name is required.");
        }
        if (req.customerEmail() == null || req.customerEmail().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your email is required.");
        }
        if (req.customerWhatsapp() == null || req.customerWhatsapp().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A WhatsApp number is required.");
        }
        if (req.selections() == null || req.selections().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose at least one option.");
        }

        List<CustomWigSelectionResponse> snapshot = req.selections().stream()
                .map(sel -> resolveSelection(businessId, sel))
                .toList();
        BigDecimal estimatedPrice = snapshot.stream()
                .map(CustomWigSelectionResponse::priceModifier)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String photoUrl = photo != null && !photo.isEmpty() ? savePhoto(businessId, photo) : null;

        CustomWigRequest request = CustomWigRequest.builder()
                .businessId(businessId)
                .customerName(req.customerName())
                .customerEmail(req.customerEmail())
                .customerWhatsapp(req.customerWhatsapp())
                .selections(writeSelections(snapshot))
                .estimatedPrice(estimatedPrice)
                .inspirationPhotoUrl(photoUrl)
                .notes(req.notes())
                .test(isTestMode(businessId))
                .build();
        request = customWigRequestRepository.save(request);
        customWigRequestRepository.flush(); // so request_number is readable below

        emailService.sendCustomWigRequestReceived(
                req.customerEmail(), req.customerName(), request.getRequestNumber(), business.getName(),
                business.getCurrency() + " " + estimatedPrice.toPlainString()
        );

        if (business.getContactEmail() != null && !business.getContactEmail().isBlank()) {
            String customerWhatsappLink = whatsAppLinkService.buildLink(req.customerWhatsapp(),
                    "Hi " + req.customerName() + ", thanks for your custom wig request — we'll be in touch with a quote soon!");
            emailService.sendNewCustomWigRequestNotification(
                    business.getContactEmail(), req.customerName(),
                    business.getCurrency() + " " + estimatedPrice.toPlainString(), customerWhatsappLink
            );
        }
        // In-app inbox — unconditional (no contactEmail gate), since it has no
        // external dependency to fail on.
        notificationService.create(businessId, "NEW_CUSTOM_WIG_REQUEST", "New custom wig request from " + req.customerName(),
                business.getCurrency() + " " + estimatedPrice.toPlainString() + " estimate", "CUSTOM_WIG_REQUEST", request.getId());

        return new CustomWigRequestCreatedResponse(request.getRequestNumber(), "Request received.", estimatedPrice);
    }

    // ---- Owner side ----

    public List<CustomWigRequestResponse> list() {
        UUID businessId = TenantContext.getBusinessId();
        planFeatureService.requireFeature(businessId, PlanFeature.CUSTOM_WIG_REQUESTS);
        return customWigRequestRepository.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(this::toResponse)
                .toList();
    }

    public CustomWigRequestDetailResponse get(UUID id) {
        CustomWigRequest request = getOwned(id);
        String paymentMethod = paymentTransactionRepository
                .findFirstBySourceTypeAndSourceIdAndDirectionAndStatusOrderByCreatedAtDesc(
                        PaymentTransaction.SourceType.CUSTOM_WIG_REQUEST, request.getId(),
                        PaymentTransaction.Direction.INCOMING, "SUCCESS")
                .map(PaymentTransaction::getMethod)
                .orElse(null);
        return new CustomWigRequestDetailResponse(
                request.getId(), request.getRequestNumber(), request.getCustomerName(), request.getCustomerEmail(),
                request.getCustomerWhatsapp(), readSelections(request.getSelections()), request.getDescription(), request.getEstimatedPrice(),
                request.getInspirationPhotoUrl(), request.getNotes(), request.getStatus(), request.getFinalPrice(),
                request.getOwnerMessage(), request.getPaymentStatus(), request.getAmountPaid(), balanceDue(request),
                paymentMethod, whatsappLinkFor(request), request.getSource(), request.getCreatedAt()
        );
    }

    // Staff logging a request that arrived through an informal channel
    // (Instagram DM, WhatsApp, a phone call) — free text + a price staff
    // types in directly, never the public widget's attribute/option picker
    // (that's still exactly how the customer-facing configurator works;
    // this is a separate, deliberately simpler path). Lands as ACCEPTED
    // immediately rather than starting the SUBMITTED->QUOTED review pipeline
    // — the price is already agreed the moment staff typed it in, same
    // spirit as a Sale or Service Order being recorded already-priced.
    // Deliberately doesn't send the "new request" EMAIL to the business's own
    // contact address — staff already know, they just typed it in. The in-app
    // bell notification still fires though: the point there is team-wide
    // visibility (Owner/Manager knowing a request landed, however it arrived),
    // not "tell the person who already knows" — same reasoning as the sale
    // notification firing regardless of who rang up the sale.
    @Transactional
    public CustomWigRequestResponse createByStaff(CreateStaffCustomWigRequestRequest req, MultipartFile photo) {
        UUID businessId = TenantContext.getBusinessId();
        planFeatureService.requireFeature(businessId, PlanFeature.CUSTOM_WIG_REQUESTS);

        if (req.customerName() == null || req.customerName().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer name is required.");
        }
        if (req.description() == null || req.description().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Describe what the customer wants.");
        }
        if (req.price() == null || req.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a price greater than zero.");
        }

        String photoUrl = photo != null && !photo.isEmpty() ? savePhoto(businessId, photo) : null;

        CustomWigRequest request = CustomWigRequest.builder()
                .businessId(businessId)
                .customerName(req.customerName())
                .customerEmail(blankToNull(req.customerEmail()))
                .customerWhatsapp(blankToNull(req.customerWhatsapp()))
                .source(blankToNull(req.source()))
                .selections(writeSelections(List.of()))
                .description(req.description().trim())
                .estimatedPrice(req.price())
                .finalPrice(req.price())
                .status("ACCEPTED")
                .inspirationPhotoUrl(photoUrl)
                .notes(req.notes())
                .test(isTestMode(businessId))
                .build();
        request = customWigRequestRepository.save(request);
        customWigRequestRepository.flush(); // so request_number is readable below

        activityLogService.log(
                "Logged a custom wig request" + (request.getSource() != null ? " (" + request.getSource() + ")" : ""),
                "CUSTOM_WIG_REQUEST", request.getId()
        );

        Business business = businessRepository.findById(businessId).orElse(null);
        String estimateLabel = (business != null ? business.getCurrency() : "GHS") + " " + req.price().toPlainString();
        notificationService.create(businessId, "NEW_CUSTOM_WIG_REQUEST", "New custom wig request from " + request.getCustomerName(),
                estimateLabel + " estimate", "CUSTOM_WIG_REQUEST", request.getId());

        if (request.getCustomerEmail() != null && business != null) {
            emailService.sendCustomWigRequestReceived(
                    request.getCustomerEmail(), request.getCustomerName(), request.getRequestNumber(), business.getName(),
                    estimateLabel
            );
        }

        return toResponse(request);
    }

    @Transactional
    public CustomWigRequestResponse quote(UUID id, BigDecimal finalPrice, String message) {
        CustomWigRequest request = getOwned(id);
        if (!"SUBMITTED".equals(request.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This request has already been responded to.");
        }
        request.setStatus("QUOTED");
        request.setFinalPrice(finalPrice);
        request.setOwnerMessage(message);
        request = customWigRequestRepository.save(request);

        activityLogService.log("Sent a quote for custom wig request #" + request.getRequestNumber(), "CUSTOM_WIG_REQUEST", request.getId());

        String businessName = businessRepository.findById(request.getBusinessId()).map(Business::getName).orElse("Ratel");
        String currency = businessRepository.findById(request.getBusinessId()).map(Business::getCurrency).orElse("GHS");
        emailService.sendCustomWigQuoteReady(
                request.getCustomerEmail(), request.getCustomerName(), request.getRequestNumber(), businessName,
                currency + " " + finalPrice.toPlainString(), message
        );

        return toResponse(request);
    }

    @Transactional
    public CustomWigRequestResponse decline(UUID id, String message) {
        CustomWigRequest request = getOwned(id);
        if ("DECLINED".equals(request.getStatus()) || "ACCEPTED".equals(request.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This request has already been responded to.");
        }
        request.setStatus("DECLINED");
        request.setOwnerMessage(message);
        request = customWigRequestRepository.save(request);

        activityLogService.log("Declined custom wig request #" + request.getRequestNumber(), "CUSTOM_WIG_REQUEST", request.getId());

        String businessName = businessRepository.findById(request.getBusinessId()).map(Business::getName).orElse("Ratel");
        emailService.sendCustomWigDeclined(request.getCustomerEmail(), request.getCustomerName(), request.getRequestNumber(), businessName, message);

        return toResponse(request);
    }

    @Transactional
    public CustomWigRequestResponse accept(UUID id) {
        CustomWigRequest request = getOwned(id);
        if (!"QUOTED".equals(request.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Send a quote before marking this accepted.");
        }
        request.setStatus("ACCEPTED");
        request = customWigRequestRepository.save(request);

        activityLogService.log("Marked custom wig request #" + request.getRequestNumber() + " as accepted", "CUSTOM_WIG_REQUEST", request.getId());

        return toResponse(request);
    }

    // ---- Payment collection (same model as SaleService/ServiceOrderService —
    // see their comments for the shared derivation logic, deliberately
    // duplicated rather than shared). Only makes sense once a finalPrice
    // exists (quoted-and-accepted, or logged already-priced by staff) — a
    // still-SUBMITTED request has nothing to collect against yet. ----

    private BigDecimal balanceDue(CustomWigRequest request) {
        if (request.getFinalPrice() == null) return null;
        return request.getFinalPrice().subtract(request.getAmountPaid()).max(BigDecimal.ZERO);
    }

    private void requireFinalPrice(CustomWigRequest request) {
        if (request.getFinalPrice() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This request doesn't have a final price yet — send a quote first.");
        }
    }

    // "Mark as paid" just means "the whole remaining balance came in, in cash."
    @Transactional
    public CustomWigRequestResponse markPaid(UUID id) {
        CustomWigRequest request = getOwned(id);
        requireFinalPrice(request);
        BigDecimal balanceDue = balanceDue(request);
        return recordPayment(id, new RecordPaymentRequest(balanceDue, com.ratel.rbms.entity.enums.PaymentMethod.CASH, "Marked paid manually"));
    }

    @Transactional
    public CustomWigRequestResponse recordPayment(UUID id, RecordPaymentRequest req) {
        CustomWigRequest request = getOwned(id);
        requireFinalPrice(request);
        BigDecimal balanceDue = balanceDue(request);
        if (balanceDue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This request is already fully paid.");
        }
        if (req.amount().compareTo(balanceDue) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That's more than the outstanding balance of GH₵" + balanceDue + ".");
        }

        BigDecimal newAmountPaid = request.getAmountPaid().add(req.amount());
        request.setAmountPaid(newAmountPaid);
        request.setPaymentStatus(derivePaymentStatus(newAmountPaid, request.getFinalPrice()));
        request = customWigRequestRepository.save(request);

        activityLogService.log(
                "Recorded a GH₵" + req.amount() + " payment on custom wig request #" + request.getRequestNumber(),
                "CUSTOM_WIG_REQUEST", request.getId()
        );

        paymentTransactionService.record(
                request.getBusinessId(), PaymentTransaction.Direction.INCOMING, PaymentTransaction.SourceType.CUSTOM_WIG_REQUEST,
                request.getId(), "MANUAL", req.method().name(), req.amount(), "SUCCESS",
                null, null, request.getCustomerWhatsapp(), req.note(), TenantContext.getUserId()
        );

        return toResponse(request);
    }

    @Transactional
    public CustomWigRequestResponse refund(UUID id, RefundRequest req) {
        CustomWigRequest request = getOwned(id);
        if (request.getAmountPaid().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing has been collected on this request to refund.");
        }

        BigDecimal refundedAmount = request.getAmountPaid();
        request.setPaymentStatus("REFUNDED");
        request = customWigRequestRepository.save(request);

        activityLogService.log("Refunded GH₵" + refundedAmount + " on custom wig request #" + request.getRequestNumber(), "CUSTOM_WIG_REQUEST", request.getId());

        paymentTransactionService.record(
                request.getBusinessId(), PaymentTransaction.Direction.OUTGOING, PaymentTransaction.SourceType.CUSTOM_WIG_REQUEST,
                request.getId(), "MANUAL", null, refundedAmount, "SUCCESS",
                null, null, request.getCustomerWhatsapp(), req.note() != null && !req.note().isBlank() ? req.note() : "Refunded",
                TenantContext.getUserId()
        );

        return toResponse(request);
    }

    // Charges the customer's mobile money wallet directly by phone number —
    // same flow as SaleService/ServiceOrderService's equivalent methods.
    public MobileMoneyChargeResponse chargeMobileMoney(UUID id, MobileMoneyChargeRequest req) {
        CustomWigRequest request = getOwned(id);
        requireFinalPrice(request);
        if ("PAID".equals(request.getPaymentStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This request is already paid.");
        }
        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(request.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment yet."));
        if (integrations.getPaystackSecretKey() == null || integrations.getPaystackSecretKey().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment yet.");
        }

        String customerEmail = resolveCustomerEmail(request);
        long amountMinorUnits = request.getFinalPrice().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        String reference = "WIGREQ-MOMO-" + request.getBusinessId().toString().substring(0, 8) + "-" + UUID.randomUUID().toString().substring(0, 8);

        PaystackService.MobileMoneyChargeResult result = paystackService.chargeMobileMoney(
                integrations.getPaystackSecretKey(), customerEmail, amountMinorUnits, req.phone(), req.provider(), reference
        );

        request.setPaystackReference(result.reference());
        if (result.success()) {
            request.setPaymentStatus("PAID");
            request.setAmountPaid(request.getFinalPrice());
            activityLogService.log("Charged mobile money for custom wig request #" + request.getRequestNumber(), "CUSTOM_WIG_REQUEST", request.getId());
        }
        customWigRequestRepository.save(request);

        paymentTransactionService.record(
                request.getBusinessId(), PaymentTransaction.Direction.INCOMING, PaymentTransaction.SourceType.CUSTOM_WIG_REQUEST,
                request.getId(), "PAYSTACK", "MOBILE_MONEY", request.getFinalPrice(),
                result.success() ? "SUCCESS" : ("failed".equalsIgnoreCase(result.status()) ? "FAILED" : "PENDING"),
                result.reference(), null, req.phone(), null, TenantContext.getUserId()
        );

        return new MobileMoneyChargeResponse(result.reference(), result.status(), result.displayText(), result.message());
    }

    // Completes a charge that came back "send_otp" — same "customer reads back
    // the SMS code" flow as SaleService/ServiceOrderService's equivalent.
    @Transactional
    public CustomWigRequestResponse submitMobileMoneyOtp(String reference, String otp) {
        CustomWigRequest request = customWigRequestRepository.findByPaystackReferenceAndBusinessId(reference, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown payment reference."));

        if ("PAID".equals(request.getPaymentStatus())) {
            return toResponse(request);
        }

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(request.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment."));

        PaystackService.ChargeResult result = paystackService.submitOtp(integrations.getPaystackSecretKey(), otp, reference);

        if (result.success()) {
            request.setPaymentStatus("PAID");
            request.setAmountPaid(request.getFinalPrice());
            request = customWigRequestRepository.save(request);
            activityLogService.log("Charged mobile money for custom wig request #" + request.getRequestNumber(), "CUSTOM_WIG_REQUEST", request.getId());
            paymentTransactionService.updateStatusByReference(request.getBusinessId(), reference, "SUCCESS");
            return toResponse(request);
        }

        if ("failed".equalsIgnoreCase(result.status())) {
            request.setPaymentStatus("FAILED");
            request = customWigRequestRepository.save(request);
            paymentTransactionService.updateStatusByReference(request.getBusinessId(), reference, "FAILED");
        }

        throw new ApiException(HttpStatus.BAD_REQUEST,
                result.message() != null && !result.message().isBlank()
                        ? result.message()
                        : "That code didn't work. If the customer got an approval prompt in their mobile money app instead of a code, use Verify once they've approved it there.");
    }

    @Transactional
    public CustomWigRequestResponse verifyPayment(String reference) {
        CustomWigRequest request = customWigRequestRepository.findByPaystackReferenceAndBusinessId(reference, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown payment reference."));

        if ("PAID".equals(request.getPaymentStatus())) {
            return toResponse(request);
        }

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(request.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment."));

        PaystackService.VerifyResult verify = paystackService.verifyTransaction(integrations.getPaystackSecretKey(), reference);

        request.setPaymentStatus(verify.success() ? "PAID" : "FAILED");
        if (verify.success()) {
            request.setAmountPaid(request.getFinalPrice());
        }
        request = customWigRequestRepository.save(request);

        if (verify.success()) {
            activityLogService.log("Confirmed payment for custom wig request #" + request.getRequestNumber(), "CUSTOM_WIG_REQUEST", request.getId());
        }

        paymentTransactionService.record(
                request.getBusinessId(), PaymentTransaction.Direction.INCOMING, PaymentTransaction.SourceType.CUSTOM_WIG_REQUEST,
                request.getId(), "PAYSTACK", "MOBILE_MONEY", request.getFinalPrice(), verify.success() ? "SUCCESS" : "FAILED",
                reference, null, null, null, TenantContext.getUserId()
        );

        return toResponse(request);
    }

    private static String derivePaymentStatus(BigDecimal amountPaid, BigDecimal total) {
        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) return "UNPAID";
        if (amountPaid.compareTo(total) >= 0) return "PAID";
        return "PARTIALLY_PAID";
    }

    private String resolveCustomerEmail(CustomWigRequest request) {
        if (request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank()) {
            return request.getCustomerEmail();
        }
        Business business = businessRepository.findById(request.getBusinessId()).orElse(null);
        String slug = business != null && business.getSlug() != null && !business.getSlug().isBlank()
                ? business.getSlug()
                : request.getBusinessId().toString().substring(0, 8);
        return "wigreq-" + request.getId().toString().substring(0, 8) + "@" + slug + ".ratelsystems.tech";
    }

    // ---- Shared helpers ----

    private CustomWigSelectionResponse resolveSelection(UUID businessId, CustomWigSelectionInput sel) {
        CustomItemAttribute attribute = attributeRepository.findByIdAndBusinessId(sel.attributeId(), businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That selection isn't valid."));
        CustomItemAttributeOption option = optionRepository.findByIdAndAttributeId(sel.optionId(), attribute.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That selection isn't valid."));
        return new CustomWigSelectionResponse(attribute.getName(), option.getLabel(), option.getPriceModifier(), option.isRequiresManualQuote());
    }

    private String savePhoto(UUID businessId, MultipartFile file) {
        if (!ALLOWED_PHOTO_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Inspiration photo must be a PNG, JPEG, or WEBP image.");
        }
        String extension = switch (file.getContentType()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String filename = UUID.randomUUID() + extension;
        try {
            Path dir = Paths.get(uploadDir, "wig-inspiration", businessId.toString());
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Couldn't save that photo. Please try again.");
        }
        return "/uploads/wig-inspiration/" + businessId + "/" + filename;
    }

    private CustomWigRequest getOwned(UUID id) {
        UUID businessId = TenantContext.getBusinessId();
        planFeatureService.requireFeature(businessId, PlanFeature.CUSTOM_WIG_REQUESTS);
        return customWigRequestRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found."));
    }

    private boolean isTestMode(UUID businessId) {
        return businessIntegrationsRepository.findByBusinessId(businessId).map(BusinessIntegrations::isTestMode).orElse(false);
    }

    private CustomItemAttributeResponse toAttributeResponse(CustomItemAttribute attribute) {
        List<com.ratel.rbms.dto.CustomItemAttributeOptionResponse> options =
                optionRepository.findAllByAttributeIdOrderBySortOrderAsc(attribute.getId()).stream()
                        .map(com.ratel.rbms.dto.CustomItemAttributeOptionResponse::from)
                        .toList();
        return CustomItemAttributeResponse.from(attribute, options);
    }

    private CustomWigRequestResponse toResponse(CustomWigRequest request) {
        return new CustomWigRequestResponse(
                request.getId(), request.getRequestNumber(), request.getCustomerName(), request.getCustomerEmail(),
                request.getCustomerWhatsapp(), request.getDescription(), request.getEstimatedPrice(), request.getStatus(), request.getFinalPrice(),
                request.getPaymentStatus(), request.getAmountPaid(), balanceDue(request),
                whatsappLinkFor(request), request.getSource(), request.getCreatedAt()
        );
    }

    private String blankToNull(String s) {
        return s != null && !s.isBlank() ? s : null;
    }

    private String whatsappLinkFor(CustomWigRequest request) {
        String message;
        if ("QUOTED".equals(request.getStatus()) && request.getFinalPrice() != null) {
            message = "Hi " + request.getCustomerName() + ", your custom wig quote (request #" + request.getRequestNumber()
                    + ") is " + request.getFinalPrice().toPlainString() + ". Let us know if you'd like to go ahead!";
        } else if ("DECLINED".equals(request.getStatus())) {
            message = "Hi " + request.getCustomerName() + ", following up on your custom wig request #" + request.getRequestNumber() + ".";
        } else {
            message = "Hi " + request.getCustomerName() + ", following up on your custom wig request #" + request.getRequestNumber() + ".";
        }
        return whatsAppLinkService.buildLink(request.getCustomerWhatsapp(), message);
    }

    private String writeSelections(List<CustomWigSelectionResponse> selections) {
        try {
            return objectMapper.writeValueAsString(selections);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Couldn't process your selections. Please try again.");
        }
    }

    private List<CustomWigSelectionResponse> readSelections(String json) {
        try {
            CollectionType type = objectMapper.getTypeFactory().constructCollectionType(List.class, CustomWigSelectionResponse.class);
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return List.of();
        }
    }
}
