package com.ratel.rbms.service;

import com.ratel.rbms.dto.BookableServiceResponse;
import com.ratel.rbms.dto.BookingCreatedResponse;
import com.ratel.rbms.dto.BookingDetailResponse;
import com.ratel.rbms.dto.BookingVerifyPaymentResponse;
import com.ratel.rbms.dto.BookingWidgetConfigResponse;
import com.ratel.rbms.dto.CheckoutResponse;
import com.ratel.rbms.dto.CreateBookingRequest;
import com.ratel.rbms.dto.CreateStaffBookingRequest;
import com.ratel.rbms.dto.WorkingHoursResponse;
import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessIntegrations;
import com.ratel.rbms.entity.BusinessWorkingHours;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.ServiceOrderItem;
import com.ratel.rbms.entity.ServicePackage;
import com.ratel.rbms.entity.ServicePackageItem;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.BusinessBlackoutDateRepository;
import com.ratel.rbms.repository.BusinessIntegrationsRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.BusinessWorkingHoursRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceOrderItemRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServicePackageItemRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.security.RateLimiterService;
import com.ratel.rbms.tenant.TenantContext;
import com.ratel.rbms.util.PhoneUtils;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Backs the public booking widget — everything here is reachable with no
 * JWT, so every method takes an explicit businessId rather than reading
 * TenantContext (which is only ever populated for authenticated requests).
 * A booking creates a real ServiceOrder immediately (RECEIVED, scheduled_at
 * set) so it shows up in the existing Service Orders list/calendar/status
 * pipeline instead of a second parallel system — Booking just carries the
 * public-booking-specific bits (customer contact, the no-login manage
 * token, payment, location) that don't belong on ServiceOrder itself.
 */
@Service
public class BookingService {

    private static final DateTimeFormatter WHEN_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a").withZone(ZoneOffset.UTC);

    private final BusinessRepository businessRepository;
    private final BusinessIntegrationsRepository businessIntegrationsRepository;
    private final BusinessWorkingHoursRepository businessWorkingHoursRepository;
    private final BusinessBlackoutDateRepository businessBlackoutDateRepository;
    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceOrderItemRepository serviceOrderItemRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final ServicePackageItemRepository servicePackageItemRepository;
    private final CustomerRepository customerRepository;
    private final BookingRepository bookingRepository;
    private final PlanFeatureService planFeatureService;
    private final PaystackService paystackService;
    private final EmailService emailService;
    private final WhatsAppLinkService whatsAppLinkService;
    private final RateLimiterService rateLimiterService;
    private final PaymentTransactionService paymentTransactionService;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ModuleAccessService moduleAccessService;
    private final String frontendUrl;

    public BookingService(
            BusinessRepository businessRepository,
            BusinessIntegrationsRepository businessIntegrationsRepository,
            BusinessWorkingHoursRepository businessWorkingHoursRepository,
            BusinessBlackoutDateRepository businessBlackoutDateRepository,
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            ServiceTypeRepository serviceTypeRepository,
            ServiceOrderRepository serviceOrderRepository,
            ServiceOrderItemRepository serviceOrderItemRepository,
            ServicePackageRepository servicePackageRepository,
            ServicePackageItemRepository servicePackageItemRepository,
            CustomerRepository customerRepository,
            BookingRepository bookingRepository,
            PlanFeatureService planFeatureService,
            PaystackService paystackService,
            EmailService emailService,
            WhatsAppLinkService whatsAppLinkService,
            RateLimiterService rateLimiterService,
            PaymentTransactionService paymentTransactionService,
            ActivityLogService activityLogService,
            NotificationService notificationService,
            UserRepository userRepository,
            ModuleAccessService moduleAccessService,
            @org.springframework.beans.factory.annotation.Value("${app.frontend-url}") String frontendUrl
    ) {
        this.businessRepository = businessRepository;
        this.businessIntegrationsRepository = businessIntegrationsRepository;
        this.businessWorkingHoursRepository = businessWorkingHoursRepository;
        this.businessBlackoutDateRepository = businessBlackoutDateRepository;
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.serviceOrderItemRepository = serviceOrderItemRepository;
        this.servicePackageRepository = servicePackageRepository;
        this.servicePackageItemRepository = servicePackageItemRepository;
        this.customerRepository = customerRepository;
        this.bookingRepository = bookingRepository;
        this.planFeatureService = planFeatureService;
        this.paystackService = paystackService;
        this.emailService = emailService;
        this.whatsAppLinkService = whatsAppLinkService;
        this.rateLimiterService = rateLimiterService;
        this.paymentTransactionService = paymentTransactionService;
        this.activityLogService = activityLogService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.moduleAccessService = moduleAccessService;
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
        List<WorkingHoursResponse> workingHours = resolveWorkingHours(business.getId()).stream()
                .map(WorkingHoursResponse::from)
                .toList();
        return new BookingWidgetConfigResponse(
                business.getId(), business.getName(), enabled, business.getCurrency(), paystackPublicKey,
                effectivePolicy(integrations, null), integrations != null ? integrations.getBookingDepositPercent() : 50,
                integrations != null && integrations.isAllowPayInPerson(),
                workingHours,
                businessWhatsappLink
        );
    }

    public List<BookableServiceResponse> listBookableServices(UUID businessId) {
        if (!planFeatureService.hasFeature(businessId, PlanFeature.BOOKING_WIDGET)) {
            return List.of();
        }
        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(businessId).orElse(null);
        List<BookableServiceResponse> results = new ArrayList<>();
        serviceCatalogItemRepository.findAllByBusinessIdAndActiveTrueAndBookableOnlineTrueOrderByNameAsc(businessId).forEach(item ->
                results.add(new BookableServiceResponse(
                        item.getId(),
                        null,
                        item.getName(),
                        item.getServiceTypeId(),
                        serviceTypeRepository.findByIdAndBusinessId(item.getServiceTypeId(), businessId)
                                .map(ServiceType::getName).orElse(null),
                        null,
                        item.getPrice(),
                        false,
                        item.isRequiresLocation(),
                        List.of(),
                        effectivePolicy(integrations, item.getPaymentPolicyOverride())
                ))
        );
        servicePackageRepository.findAllByBusinessIdAndActiveTrueAndBookableOnlineTrueOrderByNameAsc(businessId).forEach(pkg ->
                results.add(new BookableServiceResponse(
                        null,
                        pkg.getId(),
                        pkg.getName(),
                        pkg.getServiceTypeId(),
                        serviceTypeRepository.findByIdAndBusinessId(pkg.getServiceTypeId(), businessId)
                                .map(ServiceType::getName).orElse(null),
                        pkg.getDescription(),
                        pkg.getPrice(),
                        true,
                        false,
                        includedItemLabels(pkg.getId()),
                        effectivePolicy(integrations, pkg.getPaymentPolicyOverride())
                ))
        );
        return results;
    }

    private List<String> includedItemLabels(UUID packageId) {
        return servicePackageItemRepository.findAllByPackageId(packageId).stream()
                .map(item -> {
                    String name = serviceCatalogItemRepository.findById(item.getServiceCatalogId())
                            .map(ServiceCatalogItem::getName).orElse("Item");
                    return item.getQuantity() > 1 ? item.getQuantity() + "x " + name : name;
                })
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

        if ((req.serviceCatalogId() == null) == (req.packageId() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select a service.");
        }

        // Customers are tied together by this number (see PhoneUtils.normalize /
        // findFirstByBusinessIdAndPhoneNormalized below) — a bogus number like a
        // 5-digit entry would create a Customer nothing can ever be matched
        // against again, so it's rejected here rather than only normalized.
        if (!PhoneUtils.isValid(req.customerWhatsapp())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid phone number.");
        }

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(businessId).orElse(null);
        validateWorkingWindow(businessId, integrations, req.scheduledAt());

        String serviceName;
        UUID serviceTypeId;
        UUID serviceCatalogId = null;
        UUID servicePackageId = null;
        BigDecimal price;
        boolean requiresLocation;
        String itemPolicyOverride;

        if (req.packageId() != null) {
            ServicePackage pkg = servicePackageRepository.findByIdAndBusinessId(req.packageId(), businessId)
                    .filter(ServicePackage::isActive)
                    .filter(ServicePackage::isBookableOnline)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That package isn't available for booking."));

            Instant requestEnd = req.scheduledAt().plusSeconds(pkg.getDurationMinutes() * 60L);
            Instant searchFrom = req.scheduledAt().minusSeconds(pkg.getDurationMinutes() * 60L);
            List<ServiceOrder> candidates = serviceOrderRepository.findAllByBusinessIdAndServicePackageIdAndStatusNotAndScheduledAtBetween(
                    businessId, pkg.getId(), ServiceOrderStatus.CANCELLED, searchFrom, requestEnd);
            validateCapacity(pkg.getDurationMinutes(), pkg.getMaxConcurrentBookings(), candidates, req.scheduledAt());

            serviceName = pkg.getName();
            serviceTypeId = pkg.getServiceTypeId();
            servicePackageId = pkg.getId();
            price = pkg.getPrice();
            requiresLocation = false; // packages don't carry the flag today — component items might, but the package itself is the bookable unit
            itemPolicyOverride = pkg.getPaymentPolicyOverride();
        } else {
            ServiceCatalogItem catalogItem = serviceCatalogItemRepository.findByIdAndBusinessId(req.serviceCatalogId(), businessId)
                    .filter(ServiceCatalogItem::isActive)
                    .filter(ServiceCatalogItem::isBookableOnline)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That service isn't available for booking."));

            Instant requestEnd = req.scheduledAt().plusSeconds(catalogItem.getDurationMinutes() * 60L);
            Instant searchFrom = req.scheduledAt().minusSeconds(catalogItem.getDurationMinutes() * 60L);
            List<ServiceOrder> candidates = serviceOrderRepository.findAllByBusinessIdAndServiceCatalogIdAndStatusNotAndScheduledAtBetween(
                    businessId, catalogItem.getId(), ServiceOrderStatus.CANCELLED, searchFrom, requestEnd);
            validateCapacity(catalogItem.getDurationMinutes(), catalogItem.getMaxConcurrentBookings(), candidates, req.scheduledAt());

            serviceName = catalogItem.getName();
            serviceTypeId = catalogItem.getServiceTypeId();
            serviceCatalogId = catalogItem.getId();
            price = catalogItem.getPrice();
            requiresLocation = catalogItem.isRequiresLocation();
            itemPolicyOverride = catalogItem.getPaymentPolicyOverride();
        }

        if (requiresLocation && (req.customerLocation() == null || req.customerLocation().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please provide your location for this service.");
        }

        Customer customer = customerRepository.findFirstByBusinessIdAndPhoneNormalized(businessId, PhoneUtils.normalize(req.customerWhatsapp()))
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .businessId(businessId)
                        .fullName(req.customerName())
                        .phone(req.customerWhatsapp())
                        .email(req.customerEmail())
                        .build()));

        ServiceOrder order = ServiceOrder.builder()
                .businessId(businessId)
                .serviceTypeId(serviceTypeId)
                .status(ServiceOrderStatus.RECEIVED)
                .customerId(customer.getId())
                .serviceCatalogId(serviceCatalogId)
                .servicePackageId(servicePackageId)
                .notes(req.notes())
                .price(price)
                .scheduledAt(req.scheduledAt())
                .build();
        order = serviceOrderRepository.save(order);
        createServiceOrderItem(businessId, order.getId(), serviceTypeId, serviceCatalogId, serviceName, price);

        Booking booking = Booking.builder()
                .businessId(businessId)
                .serviceOrderId(order.getId())
                .customerName(req.customerName())
                .customerEmail(req.customerEmail())
                .customerWhatsapp(req.customerWhatsapp())
                .customerLocation(requiresLocation ? req.customerLocation() : null)
                .manageToken(generateToken())
                .test(isTestMode(businessId))
                .build();
        booking = bookingRepository.save(booking);
        bookingRepository.flush(); // so booking_number is readable below, same reasoning as ServiceOrderService.create()

        String manageLink = frontendUrl + "/booking/manage/" + booking.getManageToken();
        emailService.sendBookingConfirmation(
                req.customerEmail(), req.customerName(), business.getName(),
                serviceName, WHEN_FORMAT.format(req.scheduledAt()), manageLink
        );

        // Owner-facing — nobody's watching the dashboard in real time, so this
        // is how a new booking actually gets noticed, with a one-tap link to
        // message the customer straight away. Sent to business.contactEmail
        // (kept, not replaced — cheap and someone may already rely on it)
        // *and* to every Owner/Manager's own real login email, deduped by
        // lowercased address so a contact email that happens to match a
        // user's login doesn't get two copies.
        Set<String> notifiedAddresses = new HashSet<>();
        String customerWhatsappLink = whatsAppLinkService.buildLink(req.customerWhatsapp(),
                "Hi " + req.customerName() + ", thanks for booking " + serviceName + " with us!");
        if (business.getContactEmail() != null && !business.getContactEmail().isBlank()) {
            emailService.sendNewBookingNotification(
                    business.getContactEmail(), req.customerName(), serviceName,
                    WHEN_FORMAT.format(req.scheduledAt()), customerWhatsappLink
            );
            notifiedAddresses.add(business.getContactEmail().toLowerCase());
        }
        List<User> notifyRecipients = userRepository.findAllByBusinessIdAndRoleIn(businessId, List.of(Role.OWNER, Role.MANAGER)).stream()
                .filter(User::isActive)
                .toList();
        for (User recipient : notifyRecipients) {
            if (!notifiedAddresses.add(recipient.getEmail().toLowerCase())) continue; // already sent (matched contactEmail)
            emailService.sendNewBookingNotification(
                    recipient.getEmail(), req.customerName(), serviceName,
                    WHEN_FORMAT.format(req.scheduledAt()), customerWhatsappLink
            );
        }
        // In-app inbox — unconditional (no recipient-list gate), since it has
        // no external dependency to fail on.
        notificationService.create(businessId, "NEW_BOOKING", "New booking from " + req.customerName(),
                serviceName + " — " + WHEN_FORMAT.format(req.scheduledAt()), "BOOKING", booking.getId());

        boolean paymentRequired = !"NONE".equals(effectivePolicy(integrations, itemPolicyOverride));
        BigDecimal amountDue = paymentRequired ? depositAmount(price, integrations, itemPolicyOverride) : null;
        String message = paymentRequired ? "Booking received — pay to confirm." : "Booking confirmed.";

        return new BookingCreatedResponse(booking.getManageToken(), booking.getBookingNumber(), message, paymentRequired, amountDue);
    }

    // Authenticated counterpart to createBooking() above — a staff member
    // entering a phone-in request. No rate limit (that's an anonymous-abuse
    // guard), no Paystack step (staff set paymentStatus directly), and reads
    // businessId from TenantContext rather than taking it as a param since
    // this path always has an authenticated session.
    @Transactional
    public BookingCreatedResponse createStaffBooking(CreateStaffBookingRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "BOOKINGS");

        if ((req.serviceCatalogId() == null) == (req.packageId() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select a service.");
        }
        if (!List.of("UNPAID", "PAID", "PAY_IN_PERSON").contains(req.paymentStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid payment status.");
        }

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(businessId).orElse(null);
        validateWorkingWindow(businessId, integrations, req.scheduledAt());

        String serviceName;
        UUID serviceTypeId;
        UUID serviceCatalogId = null;
        UUID servicePackageId = null;
        BigDecimal price;
        boolean requiresLocation;

        if (req.packageId() != null) {
            ServicePackage pkg = servicePackageRepository.findByIdAndBusinessId(req.packageId(), businessId)
                    .filter(ServicePackage::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That package isn't available."));

            Instant requestEnd = req.scheduledAt().plusSeconds(pkg.getDurationMinutes() * 60L);
            Instant searchFrom = req.scheduledAt().minusSeconds(pkg.getDurationMinutes() * 60L);
            List<ServiceOrder> candidates = serviceOrderRepository.findAllByBusinessIdAndServicePackageIdAndStatusNotAndScheduledAtBetween(
                    businessId, pkg.getId(), ServiceOrderStatus.CANCELLED, searchFrom, requestEnd);
            validateCapacity(pkg.getDurationMinutes(), pkg.getMaxConcurrentBookings(), candidates, req.scheduledAt());

            serviceName = pkg.getName();
            serviceTypeId = pkg.getServiceTypeId();
            servicePackageId = pkg.getId();
            price = pkg.getPrice();
            requiresLocation = false;
        } else {
            ServiceCatalogItem catalogItem = serviceCatalogItemRepository.findByIdAndBusinessId(req.serviceCatalogId(), businessId)
                    .filter(ServiceCatalogItem::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That service isn't available."));

            Instant requestEnd = req.scheduledAt().plusSeconds(catalogItem.getDurationMinutes() * 60L);
            Instant searchFrom = req.scheduledAt().minusSeconds(catalogItem.getDurationMinutes() * 60L);
            List<ServiceOrder> candidates = serviceOrderRepository.findAllByBusinessIdAndServiceCatalogIdAndStatusNotAndScheduledAtBetween(
                    businessId, catalogItem.getId(), ServiceOrderStatus.CANCELLED, searchFrom, requestEnd);
            validateCapacity(catalogItem.getDurationMinutes(), catalogItem.getMaxConcurrentBookings(), candidates, req.scheduledAt());

            serviceName = catalogItem.getName();
            serviceTypeId = catalogItem.getServiceTypeId();
            serviceCatalogId = catalogItem.getId();
            price = catalogItem.getPrice();
            requiresLocation = catalogItem.isRequiresLocation();
        }

        if (requiresLocation && (req.customerLocation() == null || req.customerLocation().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A location is required for this service.");
        }

        UUID resolvedCustomerId = null;
        String resolvedCustomerName = req.customerName();
        String resolvedCustomerEmail = req.customerEmail();
        String resolvedCustomerWhatsapp = req.customerWhatsapp();

        if (req.customerId() != null) {
            Customer customer = customerRepository.findByIdAndBusinessId(req.customerId(), businessId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found."));
            resolvedCustomerId = customer.getId();
            resolvedCustomerName = customer.getFullName();
            resolvedCustomerEmail = customer.getEmail();
            resolvedCustomerWhatsapp = customer.getPhone();
        } else if (req.customerWhatsapp() != null && !req.customerWhatsapp().isBlank()) {
            Customer customer = customerRepository.findFirstByBusinessIdAndPhoneNormalized(businessId, PhoneUtils.normalize(req.customerWhatsapp()))
                    .orElseGet(() -> customerRepository.save(Customer.builder()
                            .businessId(businessId)
                            .fullName(req.customerName() != null && !req.customerName().isBlank() ? req.customerName() : "Customer")
                            .phone(req.customerWhatsapp())
                            .email(req.customerEmail())
                            .build()));
            resolvedCustomerId = customer.getId();
            resolvedCustomerName = req.customerName() != null && !req.customerName().isBlank() ? req.customerName() : customer.getFullName();
        } else if (req.customerName() != null && !req.customerName().isBlank()) {
            // Name-only call-in with no phone on file — the customer picker's
            // quick-create always attaches a real customerId before this point,
            // so this only covers older/edge clients. Still create a bare
            // Customer so resolvedCustomerId is never left null.
            Customer customer = customerRepository.save(Customer.builder()
                    .businessId(businessId)
                    .fullName(req.customerName())
                    .email(req.customerEmail())
                    .build());
            resolvedCustomerId = customer.getId();
        }

        if (resolvedCustomerName == null || resolvedCustomerName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A customer name is required.");
        }

        ServiceOrder order = ServiceOrder.builder()
                .businessId(businessId)
                .serviceTypeId(serviceTypeId)
                .status(ServiceOrderStatus.RECEIVED)
                .customerId(resolvedCustomerId)
                .serviceCatalogId(serviceCatalogId)
                .servicePackageId(servicePackageId)
                .notes(req.notes())
                .price(price)
                .assignedStaffId(req.assignedStaffId())
                .scheduledAt(req.scheduledAt())
                .build();
        order = serviceOrderRepository.save(order);
        createServiceOrderItem(businessId, order.getId(), serviceTypeId, serviceCatalogId, serviceName, price);

        Booking booking = Booking.builder()
                .businessId(businessId)
                .serviceOrderId(order.getId())
                .customerId(resolvedCustomerId)
                .customerName(resolvedCustomerName)
                .customerEmail(resolvedCustomerEmail)
                .customerWhatsapp(resolvedCustomerWhatsapp)
                .customerLocation(requiresLocation ? req.customerLocation() : null)
                .manageToken(generateToken())
                .paymentStatus(req.paymentStatus())
                .test(isTestMode(businessId))
                .build();
        booking = bookingRepository.save(booking);
        bookingRepository.flush(); // so booking_number is readable below, same reasoning as ServiceOrderService.create()

        // Staff chose PAID directly (cash already changed hands on the call) —
        // same ledger treatment as verifyPayment() below, just MANUAL/CASH
        // instead of a Paystack gateway result, and without it this payment
        // was never logged at all (found during a payment-logging audit).
        if ("PAID".equals(req.paymentStatus())) {
            paymentTransactionService.record(
                    businessId, PaymentTransaction.Direction.INCOMING, PaymentTransaction.SourceType.BOOKING,
                    booking.getId(), "MANUAL", "CASH", price, "SUCCESS",
                    null, resolvedCustomerId, resolvedCustomerWhatsapp, "Marked paid by staff at booking", TenantContext.getUserId()
            );
        }

        // No confirmation/notification emails here — unlike the public widget,
        // staff already know about this booking because they're the ones
        // entering it; there's no "customer booked while nobody was watching"
        // moment to surface.
        return new BookingCreatedResponse(booking.getManageToken(), booking.getBookingNumber(), "Booking created.", false, null);
    }

    // Mon-Sat 9am-6pm when a business has never configured any working-hours
    private List<BusinessWorkingHours> resolveWorkingHours(UUID businessId) {
        List<BusinessWorkingHours> hours = businessWorkingHoursRepository.findAllByBusinessIdOrderByDayOfWeek(businessId);
        return hours.isEmpty() ? BusinessWorkingHours.defaultHours() : hours;
    }

    private void validateWorkingWindow(UUID businessId, BusinessIntegrations integrations, Instant scheduledAt) {
        java.time.ZonedDateTime zdt = scheduledAt.atZone(ZoneOffset.UTC);
        int isoWeekday = zdt.getDayOfWeek().getValue();

        BusinessWorkingHours today = resolveWorkingHours(businessId).stream()
                .filter(h -> h.getDayOfWeek() == isoWeekday)
                .findFirst()
                .orElse(null);
        if (today == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This business isn't open on that day. Please choose another date.");
        }

        java.time.LocalTime timeOfDay = zdt.toLocalTime();
        if (timeOfDay.isBefore(today.getStartTime()) || !timeOfDay.isBefore(today.getEndTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "That time is outside business hours (" + today.getStartTime() + "–" + today.getEndTime() + "). Please choose another time.");
        }

        if (businessBlackoutDateRepository.existsByBusinessIdAndDate(businessId, zdt.toLocalDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This business is closed on that date. Please choose another day.");
        }
    }

    // Shared by both the plain-service and package booking paths — the caller
    // supplies the right overlap-candidate query (scoped to the catalog item
    // or the package respectively), this just does the exact-overlap count.
    private void validateCapacity(int durationMinutes, int maxConcurrentBookings, List<ServiceOrder> candidates, Instant scheduledAt) {
        Instant requestStart = scheduledAt;
        Instant requestEnd = scheduledAt.plusSeconds(durationMinutes * 60L);
        long overlapping = candidates.stream()
                .filter(o -> o.getScheduledAt() != null)
                .filter(o -> o.getScheduledAt().isBefore(requestEnd)
                        && o.getScheduledAt().plusSeconds(durationMinutes * 60L).isAfter(requestStart))
                .count();
        if (overlapping >= maxConcurrentBookings) {
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

        String itemPolicyOverride = resolveItemPolicyOverride(order);
        BigDecimal amount = "NONE".equals(effectivePolicy(integrations, itemPolicyOverride))
                ? order.getPrice() : depositAmount(order.getPrice(), integrations, itemPolicyOverride);
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
        BigDecimal chargedAmount = BigDecimal.valueOf(verify.amountMinorUnits()).divide(BigDecimal.valueOf(100));

        if (!verify.success()) {
            booking.setPaymentStatus("FAILED");
            bookingRepository.save(booking);
            paymentTransactionService.record(
                    booking.getBusinessId(), PaymentTransaction.Direction.INCOMING, PaymentTransaction.SourceType.BOOKING,
                    booking.getId(), "PAYSTACK", "CARD", chargedAmount, "FAILED",
                    reference, booking.getCustomerId(), null, null, null
            );
            return new BookingVerifyPaymentResponse(false, "Payment wasn't completed (" + verify.status() + ").");
        }

        booking.setPaymentStatus("PAID");
        bookingRepository.save(booking);
        paymentTransactionService.record(
                booking.getBusinessId(), PaymentTransaction.Direction.INCOMING, PaymentTransaction.SourceType.BOOKING,
                booking.getId(), "PAYSTACK", "CARD", chargedAmount, "SUCCESS",
                reference, booking.getCustomerId(), null, null, null
        );
        return new BookingVerifyPaymentResponse(true, "Payment confirmed.");
    }

    // Alternative to startPayment()/verifyPayment() — customer explicitly opts
    // to pay when they arrive instead of through Paystack. Only available when
    // the business has turned this on; re-checked here regardless of whether
    // the client only showed the button because it thought this was allowed.
    @Transactional
    public void payInPerson(String manageToken) {
        Booking booking = getByToken(manageToken);
        if ("PAID".equals(booking.getPaymentStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This booking is already paid.");
        }
        ServiceOrder order = serviceOrderRepository.findByIdAndBusinessId(booking.getServiceOrderId(), booking.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));
        if (order.getStatus() == ServiceOrderStatus.CANCELLED || order.getStatus() == ServiceOrderStatus.PICKED_UP) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This booking can no longer be changed.");
        }

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(booking.getBusinessId()).orElse(null);
        if (integrations == null || !integrations.isAllowPayInPerson()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Pay in person isn't available for this booking.");
        }

        booking.setPaymentStatus("PAY_IN_PERSON");
        bookingRepository.save(booking);
    }

    public BookingDetailResponse getByManageToken(String manageToken) {
        Booking booking = getByToken(manageToken);
        ServiceOrder order = serviceOrderRepository.findByIdAndBusinessId(booking.getServiceOrderId(), booking.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));
        String serviceName = resolveServiceName(order, booking.getBusinessId());
        String businessName = businessRepository.findById(booking.getBusinessId())
                .map(Business::getName).orElse(null);

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(booking.getBusinessId()).orElse(null);

        BigDecimal amountDue = null;
        if (!"PAID".equals(booking.getPaymentStatus()) && !"PAY_IN_PERSON".equals(booking.getPaymentStatus()) && order.getStatus() != ServiceOrderStatus.CANCELLED) {
            if (integrations != null && integrations.getPaystackSecretKey() != null && !integrations.getPaystackSecretKey().isBlank()) {
                String itemPolicyOverride = resolveItemPolicyOverride(order);
                amountDue = "NONE".equals(effectivePolicy(integrations, itemPolicyOverride))
                        ? order.getPrice() : depositAmount(order.getPrice(), integrations, itemPolicyOverride);
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
                businessWhatsappLink, booking.getCustomerLocation(),
                integrations != null ? integrations.getCancellationCutoffHours() : 0
        );
    }

    private String resolveServiceName(ServiceOrder order, UUID businessId) {
        if (order.getServicePackageId() != null) {
            return servicePackageRepository.findByIdAndBusinessId(order.getServicePackageId(), businessId)
                    .map(ServicePackage::getName).orElse(null);
        }
        if (order.getServiceCatalogId() != null) {
            return serviceCatalogItemRepository.findByIdAndBusinessId(order.getServiceCatalogId(), businessId)
                    .map(ServiceCatalogItem::getName).orElse(null);
        }
        return null;
    }

    @Transactional
    public void reschedule(String manageToken, Instant newScheduledAt) {
        Booking booking = getByToken(manageToken);
        ServiceOrder order = serviceOrderRepository.findByIdAndBusinessId(booking.getServiceOrderId(), booking.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));

        if (order.getStatus() == ServiceOrderStatus.CANCELLED || order.getStatus() == ServiceOrderStatus.PICKED_UP) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This booking can no longer be rescheduled.");
        }

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(booking.getBusinessId()).orElse(null);
        enforceCancellationCutoff(integrations, order);

        order.setScheduledAt(newScheduledAt);
        serviceOrderRepository.save(order);

        Business business = businessRepository.findById(booking.getBusinessId()).orElse(null);
        String serviceName = resolveServiceName(order, booking.getBusinessId());
        if (business != null) {
            emailService.sendBookingRescheduled(booking.getCustomerEmail(), booking.getCustomerName(), business.getName(),
                    serviceName != null ? serviceName : "your service", WHEN_FORMAT.format(newScheduledAt));
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

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(booking.getBusinessId()).orElse(null);
        enforceCancellationCutoff(integrations, order);

        order.setStatus(ServiceOrderStatus.CANCELLED);
        serviceOrderRepository.save(order);

        Business business = businessRepository.findById(booking.getBusinessId()).orElse(null);
        String serviceName = resolveServiceName(order, booking.getBusinessId());
        if (business != null) {
            emailService.sendBookingCancelled(booking.getCustomerEmail(), booking.getCustomerName(), business.getName(),
                    serviceName != null ? serviceName : "your service");
        }
    }

    // Staff-authenticated counterpart to reschedule(manageToken, ...) above —
    // looked up by id + TenantContext instead of a customer's manage token, and
    // deliberately skips enforceCancellationCutoff: that guard protects the
    // business from last-minute CUSTOMER changes, not from staff accommodating
    // one themselves.
    @Transactional
    public void rescheduleById(UUID bookingId, Instant newScheduledAt) {
        UUID businessId = TenantContext.getBusinessId();
        Booking booking = bookingRepository.findById(bookingId)
                .filter(b -> b.getBusinessId().equals(businessId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));
        ServiceOrder order = serviceOrderRepository.findByIdAndBusinessId(booking.getServiceOrderId(), businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));

        if (order.getStatus() == ServiceOrderStatus.CANCELLED || order.getStatus() == ServiceOrderStatus.PICKED_UP) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This booking can no longer be rescheduled.");
        }

        order.setScheduledAt(newScheduledAt);
        serviceOrderRepository.save(order);
        activityLogService.log("Rescheduled booking #" + booking.getBookingNumber(), "BOOKING", booking.getId());

        Business business = businessRepository.findById(businessId).orElse(null);
        String serviceName = resolveServiceName(order, businessId);
        if (business != null && booking.getCustomerEmail() != null && !booking.getCustomerEmail().isBlank()) {
            emailService.sendBookingRescheduled(booking.getCustomerEmail(), booking.getCustomerName(), business.getName(),
                    serviceName != null ? serviceName : "your service", WHEN_FORMAT.format(newScheduledAt));
        }
    }

    // A plain timestamp stamp — independent of the underlying ServiceOrder's
    // own work-progress status, since a booking can sit at RECEIVED for hours
    // before the customer is actually in the shop.
    @Transactional
    public void markArrived(UUID bookingId) {
        UUID businessId = TenantContext.getBusinessId();
        Booking booking = bookingRepository.findById(bookingId)
                .filter(b -> b.getBusinessId().equals(businessId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));

        booking.setArrivedAt(Instant.now());
        bookingRepository.save(booking);
        activityLogService.log("Marked booking #" + booking.getBookingNumber() + " as arrived", "BOOKING", booking.getId());
    }

    // Owner sets a rule like "no cancellation/reschedule within 1-2 hours of
    // the appointment" — cutoffHours <= 0 means no restriction (today's
    // behavior). Applies to both cancel and reschedule alike, same underlying
    // concern of protecting the calendar from last-minute changes.
    private void enforceCancellationCutoff(BusinessIntegrations integrations, ServiceOrder order) {
        int cutoffHours = integrations != null ? integrations.getCancellationCutoffHours() : 0;
        if (cutoffHours <= 0 || order.getScheduledAt() == null) return;
        Instant cutoff = order.getScheduledAt().minus(cutoffHours, ChronoUnit.HOURS);
        if (Instant.now().isAfter(cutoff)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "This booking can no longer be changed — it's within " + cutoffHours + " hour" + (cutoffHours == 1 ? "" : "s")
                            + " of the appointment. Please contact the business directly.");
        }
    }

    // Falls back to NONE when Paystack isn't actually configured, so a business
    // that picked DEPOSIT/FULL before finishing setup doesn't lock customers
    // out of booking entirely — they just aren't asked to pay yet. A non-blank
    // itemPolicyOverride (from the specific service/package being booked) wins
    // over the business-wide default; pass null when there's no specific item.
    private String effectivePolicy(BusinessIntegrations integrations, String itemPolicyOverride) {
        if (integrations == null) return "NONE";
        if (integrations.getPaystackSecretKey() == null || integrations.getPaystackSecretKey().isBlank()) return "NONE";
        if (itemPolicyOverride != null && !itemPolicyOverride.isBlank()) return itemPolicyOverride;
        return integrations.getBookingPaymentPolicy();
    }

    private BigDecimal depositAmount(BigDecimal price, BusinessIntegrations integrations, String itemPolicyOverride) {
        if ("DEPOSIT".equals(effectivePolicy(integrations, itemPolicyOverride))) {
            return price.multiply(BigDecimal.valueOf(integrations.getBookingDepositPercent()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return price;
    }

    // Resolves the payment-policy override of whichever item a ServiceOrder
    // was actually booked against, for the code paths (startPayment,
    // getByManageToken) that only have the order, not the original request.
    private String resolveItemPolicyOverride(ServiceOrder order) {
        if (order.getServicePackageId() != null) {
            return servicePackageRepository.findById(order.getServicePackageId())
                    .map(ServicePackage::getPaymentPolicyOverride).orElse(null);
        }
        if (order.getServiceCatalogId() != null) {
            return serviceCatalogItemRepository.findById(order.getServiceCatalogId())
                    .map(ServiceCatalogItem::getPaymentPolicyOverride).orElse(null);
        }
        return null;
    }

    private Booking getByToken(String manageToken) {
        return bookingRepository.findByManageToken(manageToken)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));
    }

    // A booking always books exactly one service or package — mirrored here as
    // a single ServiceOrderItem so every order's items list is populated
    // uniformly, whether it came from a walk-in (ServiceOrderService.create(),
    // which can have several) or a booking (always exactly one).
    private void createServiceOrderItem(UUID businessId, UUID serviceOrderId, UUID serviceTypeId, UUID serviceCatalogId, String serviceName, BigDecimal price) {
        serviceOrderItemRepository.save(ServiceOrderItem.builder()
                .businessId(businessId)
                .serviceOrderId(serviceOrderId)
                .serviceTypeId(serviceTypeId)
                .serviceCatalogId(serviceCatalogId)
                .serviceName(serviceName)
                .price(price)
                .build());
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
