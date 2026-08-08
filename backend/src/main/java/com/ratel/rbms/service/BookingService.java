package com.ratel.rbms.service;

import com.ratel.rbms.dto.BookableServiceResponse;
import com.ratel.rbms.dto.BookingCreatedResponse;
import com.ratel.rbms.dto.BookingDetailResponse;
import com.ratel.rbms.dto.BookingVerifyPaymentResponse;
import com.ratel.rbms.dto.BookingWidgetConfigResponse;
import com.ratel.rbms.dto.CheckoutResponse;
import com.ratel.rbms.dto.CreateBookingRequest;
import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessIntegrations;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.BusinessBlackoutDateRepository;
import com.ratel.rbms.repository.BusinessIntegrationsRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.security.RateLimiterService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backs the public booking widget — everything here is reachable with no
 * JWT, so every method takes an explicit businessId rather than reading
 * TenantContext (which is only ever populated for authenticated requests).
 * A booking creates a real ServiceOrder immediately (RECEIVED, scheduled_at
 * set) so it shows up in the existing Service Orders list/calendar/status
 * pipeline instead of a second parallel system — Booking just carries the
 * public-booking-specific bits (customer contact, the no-login manage
 * token, payment) that don't belong on ServiceOrder itself.
 */
@Service
public class BookingService {

    private static final DateTimeFormatter WHEN_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a").withZone(ZoneOffset.UTC);

    private final BusinessRepository businessRepository;
    private final BusinessIntegrationsRepository businessIntegrationsRepository;
    private final BusinessBlackoutDateRepository businessBlackoutDateRepository;
    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final CustomerRepository customerRepository;
    private final BookingRepository bookingRepository;
    private final PlanFeatureService planFeatureService;
    private final PaystackService paystackService;
    private final EmailService emailService;
    private final WhatsAppLinkService whatsAppLinkService;
    private final RateLimiterService rateLimiterService;
    private final String frontendUrl;

    public BookingService(
            BusinessRepository businessRepository,
            BusinessIntegrationsRepository businessIntegrationsRepository,
            BusinessBlackoutDateRepository businessBlackoutDateRepository,
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            ServiceTypeRepository serviceTypeRepository,
            ServiceOrderRepository serviceOrderRepository,
            CustomerRepository customerRepository,
            BookingRepository bookingRepository,
            PlanFeatureService planFeatureService,
            PaystackService paystackService,
            EmailService emailService,
            WhatsAppLinkService whatsAppLinkService,
            RateLimiterService rateLimiterService,
            @org.springframework.beans.factory.annotation.Value("${app.frontend-url}") String frontendUrl
    ) {
        this.businessRepository = businessRepository;
        this.businessIntegrationsRepository = businessIntegrationsRepository;
        this.businessBlackoutDateRepository = businessBlackoutDateRepository;
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.customerRepository = customerRepository;
        this.bookingRepository = bookingRepository;
        this.planFeatureService = planFeatureService;
        this.paystackService = paystackService;
        this.emailService = emailService;
        this.whatsAppLinkService = whatsAppLinkService;
        this.rateLimiterService = rateLimiterService;
        this.frontendUrl = frontendUrl;
    }

    public BookingWidgetConfigResponse getWidgetConfig(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        return buildWidgetConfig(business);
    }

    // Backs the hosted booking page (ratel.app/book/{slug}) for businesses with
    // no website of their own to embed the widget on.
    public BookingWidgetConfigResponse getWidgetConfigBySlug(String slug) {
        Business business = businessRepository.findBySlug(slug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        return buildWidgetConfig(business);
    }

    private BookingWidgetConfigResponse buildWidgetConfig(Business business) {
        boolean enabled = planFeatureService.hasFeature(business.getId(), PlanFeature.BOOKING_WIDGET);
        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(business.getId()).orElse(null);
        String paystackPublicKey = integrations != null ? integrations.getPaystackPublicKey() : null;
        if (paystackPublicKey != null && paystackPublicKey.isBlank()) paystackPublicKey = null;
        String businessWhatsappLink = integrations != null
                ? whatsAppLinkService.buildLink(integrations.getWhatsappNotifyNumber(), "Hi " + business.getName() + ", I have a question about booking.")
                : null;
        return new BookingWidgetConfigResponse(
                business.getId(), business.getName(), enabled, business.getCurrency(), paystackPublicKey,
                effectivePolicy(integrations), integrations != null ? integrations.getBookingDepositPercent() : 50,
                integrations != null ? integrations.getWorkingDays() : DEFAULT_WORKING_DAYS,
                integrations != null ? integrations.getWorkingHoursStart() : DEFAULT_HOURS_START,
                integrations != null ? integrations.getWorkingHoursEnd() : DEFAULT_HOURS_END,
                businessWhatsappLink
        );
    }

    public List<BookableServiceResponse> listBookableServices(UUID businessId) {
        if (!planFeatureService.hasFeature(businessId, PlanFeature.BOOKING_WIDGET)) {
            return List.of();
        }
        return serviceCatalogItemRepository.findAllByBusinessIdAndActiveTrueAndBookableOnlineTrueOrderByNameAsc(businessId).stream()
                .map(item -> new BookableServiceResponse(
                        item.getId(),
                        item.getName(),
                        serviceTypeRepository.findByIdAndBusinessId(item.getServiceTypeId(), businessId)
                                .map(ServiceType::getName).orElse(null),
                        item.getPrice()
                ))
                .toList();
    }

    @Transactional
    public BookingCreatedResponse createBooking(UUID businessId, CreateBookingRequest req) {
        rateLimiterService.checkAllowed("public-booking:" + businessId, 10, Duration.ofMinutes(15));
        rateLimiterService.recordAttempt("public-booking:" + businessId);

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking isn't available."));

        if (!planFeatureService.hasFeature(businessId, PlanFeature.BOOKING_WIDGET)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Booking isn't available for this business.");
        }

        ServiceCatalogItem catalogItem = serviceCatalogItemRepository.findByIdAndBusinessId(req.serviceCatalogId(), businessId)
                .filter(ServiceCatalogItem::isActive)
                .filter(ServiceCatalogItem::isBookableOnline)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That service isn't available for booking."));

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(businessId).orElse(null);
        validateSchedulingRules(businessId, integrations, catalogItem, req.scheduledAt());

        Customer customer = customerRepository.findFirstByBusinessIdAndPhone(businessId, req.customerWhatsapp())
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .businessId(businessId)
                        .fullName(req.customerName())
                        .phone(req.customerWhatsapp())
                        .email(req.customerEmail())
                        .build()));

        ServiceOrder order = ServiceOrder.builder()
                .businessId(businessId)
                .serviceTypeId(catalogItem.getServiceTypeId())
                .status(ServiceOrderStatus.RECEIVED)
                .customerId(customer.getId())
                .serviceCatalogId(catalogItem.getId())
                .notes(req.notes())
                .price(catalogItem.getPrice())
                .scheduledAt(req.scheduledAt())
                .build();
        order = serviceOrderRepository.save(order);

        Booking booking = Booking.builder()
                .businessId(businessId)
                .serviceOrderId(order.getId())
                .customerName(req.customerName())
                .customerEmail(req.customerEmail())
                .customerWhatsapp(req.customerWhatsapp())
                .manageToken(generateToken())
                .test(isTestMode(businessId))
                .build();
        booking = bookingRepository.save(booking);
        bookingRepository.flush(); // so booking_number is readable below, same reasoning as ServiceOrderService.create()

        String manageLink = frontendUrl + "/booking/manage/" + booking.getManageToken();
        emailService.sendBookingConfirmation(
                req.customerEmail(), req.customerName(), business.getName(),
                catalogItem.getName(), WHEN_FORMAT.format(req.scheduledAt()), manageLink
        );

        // Owner-facing — nobody's watching the dashboard in real time, so this
        // is how a new booking actually gets noticed, with a one-tap link to
        // message the customer straight away.
        if (business.getContactEmail() != null && !business.getContactEmail().isBlank()) {
            String customerWhatsappLink = whatsAppLinkService.buildLink(req.customerWhatsapp(),
                    "Hi " + req.customerName() + ", thanks for booking " + catalogItem.getName() + " with us!");
            emailService.sendNewBookingNotification(
                    business.getContactEmail(), req.customerName(), catalogItem.getName(),
                    WHEN_FORMAT.format(req.scheduledAt()), customerWhatsappLink
            );
        }

        boolean paymentRequired = !"NONE".equals(effectivePolicy(integrations));
        BigDecimal amountDue = paymentRequired ? depositAmount(catalogItem.getPrice(), integrations) : null;
        String message = paymentRequired ? "Booking received — pay to confirm." : "Booking confirmed.";

        return new BookingCreatedResponse(booking.getManageToken(), booking.getBookingNumber(), message, paymentRequired, amountDue);
    }

    // Business-wide working days/hours default to Mon-Sat 9am-6pm when no
    // BusinessIntegrations row exists yet, matching the entity's own defaults —
    // a business shouldn't lose its scheduling guardrails just because nobody's
    // opened the Integrations page yet.
    private static final List<Integer> DEFAULT_WORKING_DAYS = List.of(1, 2, 3, 4, 5, 6);
    private static final java.time.LocalTime DEFAULT_HOURS_START = java.time.LocalTime.of(9, 0);
    private static final java.time.LocalTime DEFAULT_HOURS_END = java.time.LocalTime.of(18, 0);

    private void validateSchedulingRules(UUID businessId, BusinessIntegrations integrations, ServiceCatalogItem catalogItem, Instant scheduledAt) {
        List<Integer> workingDays = integrations != null ? integrations.getWorkingDays() : DEFAULT_WORKING_DAYS;
        java.time.LocalTime hoursStart = integrations != null ? integrations.getWorkingHoursStart() : DEFAULT_HOURS_START;
        java.time.LocalTime hoursEnd = integrations != null ? integrations.getWorkingHoursEnd() : DEFAULT_HOURS_END;

        java.time.ZonedDateTime zdt = scheduledAt.atZone(ZoneOffset.UTC);
        int isoWeekday = zdt.getDayOfWeek().getValue();
        if (!workingDays.contains(isoWeekday)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This business isn't open on that day. Please choose another date.");
        }

        java.time.LocalTime timeOfDay = zdt.toLocalTime();
        if (timeOfDay.isBefore(hoursStart) || !timeOfDay.isBefore(hoursEnd)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "That time is outside business hours (" + hoursStart + "–" + hoursEnd + "). Please choose another time.");
        }

        if (businessBlackoutDateRepository.existsByBusinessIdAndDate(businessId, zdt.toLocalDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This business is closed on that date. Please choose another day.");
        }

        int durationMinutes = catalogItem.getDurationMinutes();
        Instant requestStart = scheduledAt;
        Instant requestEnd = scheduledAt.plusSeconds(durationMinutes * 60L);
        Instant searchFrom = scheduledAt.minusSeconds(durationMinutes * 60L);

        List<ServiceOrder> candidates = serviceOrderRepository.findAllByBusinessIdAndServiceCatalogIdAndStatusNotAndScheduledAtBetween(
                businessId, catalogItem.getId(), ServiceOrderStatus.CANCELLED, searchFrom, requestEnd);
        long overlapping = candidates.stream()
                .filter(o -> o.getScheduledAt() != null)
                .filter(o -> o.getScheduledAt().isBefore(requestEnd)
                        && o.getScheduledAt().plusSeconds(durationMinutes * 60L).isAfter(requestStart))
                .count();
        if (overlapping >= catalogItem.getMaxConcurrentBookings()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That time is fully booked — please choose another slot.");
        }
    }

    public CheckoutResponse startPayment(String manageToken) {
        Booking booking = getByToken(manageToken);
        if ("PAID".equals(booking.getPaymentStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This booking is already paid.");
        }
        ServiceOrder order = serviceOrderRepository.findByIdAndBusinessId(booking.getServiceOrderId(), booking.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(booking.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment yet."));
        if (integrations.getPaystackSecretKey() == null || integrations.getPaystackSecretKey().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment yet.");
        }

        BigDecimal amount = "NONE".equals(effectivePolicy(integrations)) ? order.getPrice() : depositAmount(order.getPrice(), integrations);
        long amountMinorUnits = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        String reference = "BOOKING-" + booking.getBusinessId().toString().substring(0, 8) + "-" + UUID.randomUUID().toString().substring(0, 8);

        PaystackService.InitResult init = paystackService.initializeTransaction(
                integrations.getPaystackSecretKey(),
                booking.getCustomerEmail(),
                amountMinorUnits,
                reference,
                Map.of("bookingId", booking.getId().toString())
        );

        booking.setPaystackReference(init.reference());
        bookingRepository.save(booking);

        return new CheckoutResponse(init.accessCode(), init.reference());
    }

    @Transactional
    public BookingVerifyPaymentResponse verifyPayment(String reference) {
        Booking booking = bookingRepository.findByPaystackReference(reference)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown payment reference."));

        if ("PAID".equals(booking.getPaymentStatus())) {
            return new BookingVerifyPaymentResponse(true, "Already confirmed.");
        }

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(booking.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment."));

        PaystackService.VerifyResult verify = paystackService.verifyTransaction(integrations.getPaystackSecretKey(), reference);

        if (!verify.success()) {
            booking.setPaymentStatus("FAILED");
            bookingRepository.save(booking);
            return new BookingVerifyPaymentResponse(false, "Payment wasn't completed (" + verify.status() + ").");
        }

        booking.setPaymentStatus("PAID");
        bookingRepository.save(booking);
        return new BookingVerifyPaymentResponse(true, "Payment confirmed.");
    }

    public BookingDetailResponse getByManageToken(String manageToken) {
        Booking booking = getByToken(manageToken);
        ServiceOrder order = serviceOrderRepository.findByIdAndBusinessId(booking.getServiceOrderId(), booking.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));
        String serviceName = order.getServiceCatalogId() != null
                ? serviceCatalogItemRepository.findByIdAndBusinessId(order.getServiceCatalogId(), booking.getBusinessId())
                        .map(ServiceCatalogItem::getName).orElse(null)
                : null;
        String businessName = businessRepository.findById(booking.getBusinessId())
                .map(Business::getName).orElse(null);

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(booking.getBusinessId()).orElse(null);

        BigDecimal amountDue = null;
        if (!"PAID".equals(booking.getPaymentStatus()) && order.getStatus() != ServiceOrderStatus.CANCELLED) {
            if (integrations != null && integrations.getPaystackSecretKey() != null && !integrations.getPaystackSecretKey().isBlank()) {
                amountDue = "NONE".equals(effectivePolicy(integrations)) ? order.getPrice() : depositAmount(order.getPrice(), integrations);
            }
        }

        String currency = businessRepository.findById(booking.getBusinessId()).map(Business::getCurrency).orElse("GHS");
        String businessWhatsappLink = integrations != null
                ? whatsAppLinkService.buildLink(integrations.getWhatsappNotifyNumber(),
                        "Hi, I have a question about my booking #" + booking.getBookingNumber() + ".")
                : null;

        return new BookingDetailResponse(
                booking.getBookingNumber(), businessName, serviceName, order.getStatus().name(),
                order.getScheduledAt(), order.getPrice(), booking.getPaymentStatus(), booking.getCustomerName(), amountDue, currency,
                businessWhatsappLink
        );
    }

    @Transactional
    public void reschedule(String manageToken, Instant newScheduledAt) {
        Booking booking = getByToken(manageToken);
        ServiceOrder order = serviceOrderRepository.findByIdAndBusinessId(booking.getServiceOrderId(), booking.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));

        if (order.getStatus() == ServiceOrderStatus.CANCELLED || order.getStatus() == ServiceOrderStatus.PICKED_UP) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This booking can no longer be rescheduled.");
        }

        order.setScheduledAt(newScheduledAt);
        serviceOrderRepository.save(order);

        Business business = businessRepository.findById(booking.getBusinessId()).orElse(null);
        String serviceName = order.getServiceCatalogId() != null
                ? serviceCatalogItemRepository.findByIdAndBusinessId(order.getServiceCatalogId(), booking.getBusinessId())
                        .map(ServiceCatalogItem::getName).orElse("your service")
                : "your service";
        if (business != null) {
            emailService.sendBookingRescheduled(booking.getCustomerEmail(), booking.getCustomerName(), business.getName(),
                    serviceName, WHEN_FORMAT.format(newScheduledAt));
        }
    }

    @Transactional
    public void cancel(String manageToken) {
        Booking booking = getByToken(manageToken);
        ServiceOrder order = serviceOrderRepository.findByIdAndBusinessId(booking.getServiceOrderId(), booking.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));

        if (order.getStatus() == ServiceOrderStatus.PICKED_UP) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This booking is already complete.");
        }

        order.setStatus(ServiceOrderStatus.CANCELLED);
        serviceOrderRepository.save(order);

        Business business = businessRepository.findById(booking.getBusinessId()).orElse(null);
        String serviceName = order.getServiceCatalogId() != null
                ? serviceCatalogItemRepository.findByIdAndBusinessId(order.getServiceCatalogId(), booking.getBusinessId())
                        .map(ServiceCatalogItem::getName).orElse("your service")
                : "your service";
        if (business != null) {
            emailService.sendBookingCancelled(booking.getCustomerEmail(), booking.getCustomerName(), business.getName(), serviceName);
        }
    }

    // Falls back to NONE when Paystack isn't actually configured, so a business
    // that picked DEPOSIT/FULL before finishing setup doesn't lock customers
    // out of booking entirely — they just aren't asked to pay yet.
    private String effectivePolicy(BusinessIntegrations integrations) {
        if (integrations == null) return "NONE";
        if (integrations.getPaystackSecretKey() == null || integrations.getPaystackSecretKey().isBlank()) return "NONE";
        return integrations.getBookingPaymentPolicy();
    }

    private BigDecimal depositAmount(BigDecimal price, BusinessIntegrations integrations) {
        if ("DEPOSIT".equals(effectivePolicy(integrations))) {
            return price.multiply(BigDecimal.valueOf(integrations.getBookingDepositPercent()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return price;
    }

    private Booking getByToken(String manageToken) {
        return bookingRepository.findByManageToken(manageToken)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));
    }

    private boolean isTestMode(UUID businessId) {
        return businessIntegrationsRepository.findByBusinessId(businessId).map(BusinessIntegrations::isTestMode).orElse(false);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
