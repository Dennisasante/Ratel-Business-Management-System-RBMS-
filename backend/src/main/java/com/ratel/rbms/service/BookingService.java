package com.ratel.rbms.service;

import com.ratel.rbms.dto.AvailabilityCheckResponse;
import com.ratel.rbms.dto.BookableServiceResponse;
import com.ratel.rbms.dto.BookingCreatedResponse;
import com.ratel.rbms.dto.BookingDetailResponse;
import com.ratel.rbms.dto.BookingVerifyPaymentResponse;
import com.ratel.rbms.dto.BookingWidgetConfigResponse;
import com.ratel.rbms.dto.CheckoutResponse;
import com.ratel.rbms.dto.CreateBookingRequest;
import com.ratel.rbms.dto.CreateStaffBookingRequest;
import com.ratel.rbms.dto.PackageOptionsResponse;
import com.ratel.rbms.dto.PackagePricingPreviewResponse;
import com.ratel.rbms.dto.WorkingHoursResponse;
import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessIntegrations;
import com.ratel.rbms.entity.BusinessWorkingHours;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.OfferingBookingConfig;
import com.ratel.rbms.entity.Option;
import com.ratel.rbms.entity.PackageComponent;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.ServiceOrderItem;
import com.ratel.rbms.entity.ServiceOrderLineSnapshot;
import com.ratel.rbms.entity.ServicePackage;
import com.ratel.rbms.entity.ServicePackageItem;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.SubstitutionRule;
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
import com.ratel.rbms.repository.OfferingBookingConfigRepository;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.OptionRepository;
import com.ratel.rbms.repository.PackageComponentRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceOrderItemRepository;
import com.ratel.rbms.repository.ServiceOrderLineSnapshotRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServicePackageItemRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.repository.SubstitutionRuleRepository;
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
import java.util.Optional;
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
    private final PolicyEngine policyEngine;
    private final String frontendUrl;

    // Phase 5C — canonical cutover collaborators. See BookingCutoverStateResolver's own class
    // comment for the "consult exactly once, branch entirely from that result" discipline every
    // method below follows.
    private final BookingCutoverStateResolver bookingCutoverStateResolver;
    private final OfferingResolutionService offeringResolutionService;
    private final OfferingRepository offeringRepository;
    private final OfferingBookingConfigRepository offeringBookingConfigRepository;
    private final PackageComponentRepository packageComponentRepository;
    private final OptionRepository optionRepository;
    private final PackagePricingService packagePricingService;
    private final ServiceOrderLineSnapshotRepository serviceOrderLineSnapshotRepository;
    private final SubstitutionRuleRepository substitutionRuleRepository;

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
            PolicyEngine policyEngine,
            BookingCutoverStateResolver bookingCutoverStateResolver,
            OfferingResolutionService offeringResolutionService,
            OfferingRepository offeringRepository,
            OfferingBookingConfigRepository offeringBookingConfigRepository,
            PackageComponentRepository packageComponentRepository,
            OptionRepository optionRepository,
            PackagePricingService packagePricingService,
            ServiceOrderLineSnapshotRepository serviceOrderLineSnapshotRepository,
            SubstitutionRuleRepository substitutionRuleRepository,
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
        this.policyEngine = policyEngine;
        this.bookingCutoverStateResolver = bookingCutoverStateResolver;
        this.offeringResolutionService = offeringResolutionService;
        this.offeringRepository = offeringRepository;
        this.offeringBookingConfigRepository = offeringBookingConfigRepository;
        this.packageComponentRepository = packageComponentRepository;
        this.optionRepository = optionRepository;
        this.packagePricingService = packagePricingService;
        this.serviceOrderLineSnapshotRepository = serviceOrderLineSnapshotRepository;
        this.substitutionRuleRepository = substitutionRuleRepository;
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
        // Phase 5C — consulted exactly once, entire method behaviour branches from this single
        // result (BookingCutoverStateResolver's own frozen discipline). AI inherits automatically:
        // AiToolService.listBookableServices/getServiceDetails call this exact method.
        if (bookingCutoverStateResolver.useCanonical(businessId)) {
            return listBookableServicesCanonical(businessId);
        }
        return listBookableServicesLegacy(businessId);
    }

    private List<BookableServiceResponse> listBookableServicesLegacy(UUID businessId) {
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

    // Phase 5C — canonical mirror of listBookableServicesLegacy(). Catalog MEMBERSHIP (which
    // items exist/are active+bookableOnline) still comes from legacy — Phase 5C introduces no
    // independent canonical catalog-editing surface (Revision 1 §12/coexistence rule); only each
    // item's DISPLAYED price/label/duration/etc. is sourced canonically. An active+bookableOnline
    // legacy item with no (yet) canonical mapping or a broken canonical calculation is skipped
    // rather than shown with wrong/absent data — this can only happen via a same-business race
    // right after a legacy edit, since CANONICAL_ENABLED itself requires zero-mismatch
    // verification, which already proves every such item resolves and prices cleanly.
    private List<BookableServiceResponse> listBookableServicesCanonical(UUID businessId) {
        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(businessId).orElse(null);
        List<BookableServiceResponse> results = new ArrayList<>();

        for (ServiceCatalogItem item : serviceCatalogItemRepository.findAllByBusinessIdAndActiveTrueAndBookableOnlineTrueOrderByNameAsc(businessId)) {
            UUID offeringId = offeringResolutionService.resolveOfferingId(
                    businessId, OfferingResolutionService.LegacyType.SERVICE_CATALOG_ITEM, item.getId());
            if (offeringId == null) continue;
            Offering offering = offeringRepository.findByIdAndBusinessId(offeringId, businessId).orElse(null);
            OfferingBookingConfig config = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offeringId, businessId).orElse(null);
            if (offering == null || !offering.isActive() || config == null || !config.isBookableOnline()) continue;
            PricingResult pricing = packagePricingService.calculate(businessId, offeringId, Map.of(), Map.of());
            if (!pricing.valid() || pricing.finalPrice() == null) continue;
            results.add(new BookableServiceResponse(
                    item.getId(), null, offering.getName(), item.getServiceTypeId(),
                    serviceTypeRepository.findByIdAndBusinessId(item.getServiceTypeId(), businessId)
                            .map(ServiceType::getName).orElse(null),
                    null, pricing.finalPrice(), false, config.isRequiresLocation(), List.of(),
                    effectivePolicy(integrations, config.getPaymentPolicyOverride())
            ));
        }

        for (ServicePackage pkg : servicePackageRepository.findAllByBusinessIdAndActiveTrueAndBookableOnlineTrueOrderByNameAsc(businessId)) {
            UUID offeringId = offeringResolutionService.resolveOfferingId(
                    businessId, OfferingResolutionService.LegacyType.SERVICE_PACKAGE, pkg.getId());
            if (offeringId == null) continue;
            Offering offering = offeringRepository.findByIdAndBusinessId(offeringId, businessId).orElse(null);
            OfferingBookingConfig config = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offeringId, businessId).orElse(null);
            if (offering == null || !offering.isActive() || config == null || !config.isBookableOnline()) continue;
            Map<UUID, Set<UUID>> selections = offeringResolutionService.defaultSelections(businessId, offeringId);
            PricingResult pricing = packagePricingService.calculate(businessId, offeringId, selections, Map.of());
            if (!pricing.valid() || pricing.manualQuoteRequired() || pricing.finalPrice() == null) continue;
            results.add(new BookableServiceResponse(
                    null, pkg.getId(), offering.getName(), pkg.getServiceTypeId(),
                    serviceTypeRepository.findByIdAndBusinessId(pkg.getServiceTypeId(), businessId)
                            .map(ServiceType::getName).orElse(null),
                    pkg.getDescription(), pricing.finalPrice(), true, config.isRequiresLocation(),
                    canonicalIncludedItemLabels(businessId, offeringId),
                    effectivePolicy(integrations, config.getPaymentPolicyOverride())
            ));
        }
        return results;
    }

    // Canonical mirror of includedItemLabels(UUID) — reads the backfilled default Option labels
    // (already "Nx Name"-formatted by PackageContentBackfillService) in display order, rather than
    // re-deriving from ServicePackageItem/ServiceCatalogItem directly.
    private List<String> canonicalIncludedItemLabels(UUID businessId, UUID offeringId) {
        List<String> labels = new ArrayList<>();
        for (PackageComponent component : packageComponentRepository.findAllByBusinessIdAndOfferingIdOrderByDisplayOrderAsc(businessId, offeringId)) {
            if (component.getDefaultOptionId() == null) continue;
            optionRepository.findByIdAndBusinessId(component.getDefaultOptionId(), businessId)
                    .ifPresent(option -> labels.add(option.getLabel()));
        }
        return labels;
    }

    // Added for Tallia AI's getServiceDetails tool — a thin filter over the
    // same safe, already-public listBookableServices() rather than a new
    // repository query, so it can never expose anything that list doesn't
    // already expose (e.g. an inactive or not-bookable-online item).
    public Optional<BookableServiceResponse> getBookableServiceDetail(UUID businessId, UUID id) {
        return listBookableServices(businessId).stream()
                .filter(s -> id.equals(s.serviceCatalogId()) || id.equals(s.packageId()))
                .findFirst();
    }

    // Added for Tallia AI's checkAvailability tool. Deliberately does NOT
    // reimplement the availability algorithm — it resolves the same
    // duration/maxConcurrentBookings/overlap-candidates inputs createBooking()
    // already resolves, then calls the exact same validateWorkingWindow()/
    // validateCapacity() private methods createBooking() calls, just without
    // ever inserting anything. A rejection there normally throws ApiException
    // (that's the right behavior for an actual booking attempt); here it's
    // caught and turned into a plain available=false/reason result instead,
    // since "not available" is an expected, common answer for a check, not
    // an error.
    public AvailabilityCheckResponse checkAvailability(UUID businessId, UUID serviceCatalogId, UUID packageId, Instant scheduledAt) {
        try {
            BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(businessId).orElse(null);
            validateWorkingWindow(businessId, integrations, scheduledAt); // business-level, unaffected by cutover state

            // Phase 5C — consulted exactly once; AiToolService.checkAvailability calls this exact
            // method, so it inherits the same branch automatically.
            boolean canonical = bookingCutoverStateResolver.useCanonical(businessId);

            if (packageId != null) {
                if (canonical) {
                    ResolvedOffering resolved = resolveCanonicalPackage(businessId, packageId, false, true);
                    Instant requestEnd = scheduledAt.plusSeconds(resolved.config().getDurationMinutes() * 60L);
                    Instant searchFrom = scheduledAt.minusSeconds(resolved.config().getDurationMinutes() * 60L);
                    List<ServiceOrder> candidates = serviceOrderRepository.findAllByBusinessIdAndServicePackageIdAndStatusNotAndScheduledAtBetween(
                            businessId, packageId, ServiceOrderStatus.CANCELLED, searchFrom, requestEnd);
                    validateCapacity(resolved.config().getDurationMinutes(), resolved.config().getMaxConcurrentBookings(), candidates, scheduledAt);
                } else {
                    ServicePackage pkg = servicePackageRepository.findByIdAndBusinessId(packageId, businessId)
                            .filter(ServicePackage::isActive)
                            .filter(ServicePackage::isBookableOnline)
                            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That package isn't available for booking."));
                    Instant requestEnd = scheduledAt.plusSeconds(pkg.getDurationMinutes() * 60L);
                    Instant searchFrom = scheduledAt.minusSeconds(pkg.getDurationMinutes() * 60L);
                    List<ServiceOrder> candidates = serviceOrderRepository.findAllByBusinessIdAndServicePackageIdAndStatusNotAndScheduledAtBetween(
                            businessId, pkg.getId(), ServiceOrderStatus.CANCELLED, searchFrom, requestEnd);
                    validateCapacity(pkg.getDurationMinutes(), pkg.getMaxConcurrentBookings(), candidates, scheduledAt);
                }
            } else if (serviceCatalogId != null) {
                if (canonical) {
                    ResolvedOffering resolved = resolveCanonicalService(businessId, serviceCatalogId, false, true);
                    Instant requestEnd = scheduledAt.plusSeconds(resolved.config().getDurationMinutes() * 60L);
                    Instant searchFrom = scheduledAt.minusSeconds(resolved.config().getDurationMinutes() * 60L);
                    List<ServiceOrder> candidates = serviceOrderRepository.findAllByBusinessIdAndServiceCatalogIdAndStatusNotAndScheduledAtBetween(
                            businessId, serviceCatalogId, ServiceOrderStatus.CANCELLED, searchFrom, requestEnd);
                    validateCapacity(resolved.config().getDurationMinutes(), resolved.config().getMaxConcurrentBookings(), candidates, scheduledAt);
                } else {
                    ServiceCatalogItem item = serviceCatalogItemRepository.findByIdAndBusinessId(serviceCatalogId, businessId)
                            .filter(ServiceCatalogItem::isActive)
                            .filter(ServiceCatalogItem::isBookableOnline)
                            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That service isn't available for booking."));
                    Instant requestEnd = scheduledAt.plusSeconds(item.getDurationMinutes() * 60L);
                    Instant searchFrom = scheduledAt.minusSeconds(item.getDurationMinutes() * 60L);
                    List<ServiceOrder> candidates = serviceOrderRepository.findAllByBusinessIdAndServiceCatalogIdAndStatusNotAndScheduledAtBetween(
                            businessId, item.getId(), ServiceOrderStatus.CANCELLED, searchFrom, requestEnd);
                    validateCapacity(item.getDurationMinutes(), item.getMaxConcurrentBookings(), candidates, scheduledAt);
                }
            } else {
                return AvailabilityCheckResponse.unavailable("Select a service.");
            }
            return AvailabilityCheckResponse.ok();
        } catch (ApiException e) {
            return AvailabilityCheckResponse.unavailable(e.getMessage());
        }
    }

    // ==================== Phase 5C — canonical Offering resolution helpers ====================

    /** offering + its booking config, resolved and (optionally) row-locked together. */
    private record ResolvedOffering(UUID offeringId, Offering offering, OfferingBookingConfig config) {
    }

    // lock=true acquires the capacity-race correctness fix's Offering-row FOR UPDATE (frozen
    // design §10) BEFORE any capacity-dependent read/insert — used by the actual booking-creation
    // paths, never by a pure availability check (lock=false there, since nothing is inserted).
    // enforceBookableOnline mirrors the exact legacy asymmetry re-confirmed by fresh source: the
    // public/AI path filters bookableOnline, the staff path does not.
    private ResolvedOffering resolveCanonicalPackage(UUID businessId, UUID packageId, boolean lock, boolean enforceBookableOnline) {
        UUID offeringId = offeringResolutionService.resolveOfferingId(
                businessId, OfferingResolutionService.LegacyType.SERVICE_PACKAGE, packageId);
        if (offeringId == null) {
            // Never silently fall back to legacy while canonical is authoritative (frozen design §5).
            throw new ApiException(HttpStatus.BAD_REQUEST, "That package isn't available for booking.");
        }
        Offering offering = lock
                ? offeringRepository.findByIdAndBusinessIdForUpdate(offeringId, businessId).orElse(null)
                : offeringRepository.findByIdAndBusinessId(offeringId, businessId).orElse(null);
        if (offering == null || !offering.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That package isn't available for booking.");
        }
        OfferingBookingConfig config = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offeringId, businessId).orElse(null);
        if (config == null || (enforceBookableOnline && !config.isBookableOnline())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That package isn't available for booking.");
        }
        return new ResolvedOffering(offeringId, offering, config);
    }

    private ResolvedOffering resolveCanonicalService(UUID businessId, UUID serviceCatalogId, boolean lock, boolean enforceBookableOnline) {
        UUID offeringId = offeringResolutionService.resolveOfferingId(
                businessId, OfferingResolutionService.LegacyType.SERVICE_CATALOG_ITEM, serviceCatalogId);
        if (offeringId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That service isn't available for booking.");
        }
        Offering offering = lock
                ? offeringRepository.findByIdAndBusinessIdForUpdate(offeringId, businessId).orElse(null)
                : offeringRepository.findByIdAndBusinessId(offeringId, businessId).orElse(null);
        if (offering == null || !offering.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That service isn't available for booking.");
        }
        OfferingBookingConfig config = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offeringId, businessId).orElse(null);
        if (config == null || (enforceBookableOnline && !config.isBookableOnline())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That service isn't available for booking.");
        }
        return new ResolvedOffering(offeringId, offering, config);
    }

    /** One line of the historical transaction snapshot, pending persistence until the booking commits. */
    private record PendingLineSnapshot(String label, BigDecimal amount, Integer quantity, UUID componentId, UUID optionId) {
    }

    // Line amounts always sum to exactly pricing.finalPrice() (== the price ServiceOrder.price is
    // set to in the same transaction) — the invariant that makes SERVICE_ORDER_FIELD_DRIFT
    // provable by construction rather than merely asserted. basePrice is its own explicit line
    // (never folded silently into the first adjustment) since every backfilled package component
    // adjustment is 0.00 by construction (Revision 4 §3/§6) — omitting a base-price line would
    // otherwise leave the snapshot summing to zero while the order is charged its full price.
    private List<PendingLineSnapshot> snapshotsFromPricing(PricingResult pricing, UUID businessId, String offeringName) {
        return snapshotsFromPricing(pricing, businessId, offeringName, 1);
    }

    // partySize multiplies every line's amount (and, where already present, its own submitted
    // quantity) so the snapshot keeps summing to exactly the party-size-adjusted total the
    // ServiceOrder is actually charged (Restaurant-AI-demo phase) — the same
    // "provable by construction" invariant this method already upheld for partySize==1, just
    // carried through the multiplication. The multiplication itself happens here, in plain Java,
    // never inside PackagePricingService (which has no concept of party size) and never by the AI.
    private List<PendingLineSnapshot> snapshotsFromPricing(PricingResult pricing, UUID businessId, String offeringName, int partySize) {
        List<PendingLineSnapshot> lines = new ArrayList<>();
        if (pricing.basePrice().compareTo(BigDecimal.ZERO) != 0 || pricing.adjustments().isEmpty()) {
            lines.add(new PendingLineSnapshot(offeringName, scaleAmount(pricing.basePrice(), partySize), null, null, null));
        }
        for (PricingAdjustment adjustment : pricing.adjustments()) {
            String label = adjustment.optionId() != null
                    ? optionRepository.findByIdAndBusinessId(adjustment.optionId(), businessId).map(o -> o.getLabel()).orElse("Item")
                    : "Item";
            Integer scaledQuantity = adjustment.quantity() != null ? adjustment.quantity() * partySize : null;
            lines.add(new PendingLineSnapshot(label, scaleAmount(adjustment.amount(), partySize), scaledQuantity,
                    adjustment.componentId(), adjustment.optionId()));
        }
        return lines;
    }

    private BigDecimal scaleAmount(BigDecimal amount, int partySize) {
        return partySize == 1 ? amount : amount.multiply(BigDecimal.valueOf(partySize));
    }

    // Restaurant-AI-demo phase — plain human-readable line, not a new column. Deliberately
    // formatted as a standalone "Party size: N" line (not folded into free text) so a future
    // reminder feature could still parse it back out of ServiceOrder.notes if it ever needs to,
    // without this phase building any reminder logic itself (explicitly out of scope here).
    private String withPartySizeNote(String notes, Integer partySize) {
        if (partySize == null) return notes;
        String note = "Party size: " + partySize;
        return (notes == null || notes.isBlank()) ? note : notes + "\n" + note;
    }

    private void persistLineSnapshots(UUID businessId, UUID serviceOrderId, List<PendingLineSnapshot> lines) {
        for (PendingLineSnapshot line : lines) {
            serviceOrderLineSnapshotRepository.save(ServiceOrderLineSnapshot.builder()
                    .businessId(businessId)
                    .serviceOrderId(serviceOrderId)
                    .label(line.label())
                    .amount(line.amount())
                    .quantity(line.quantity())
                    .sourceComponentId(line.componentId())
                    .sourceOptionId(line.optionId())
                    .build());
        }
    }

    // Capacity-race correctness fix (frozen design §10) applies to BOTH the legacy and canonical
    // pricing paths equally — it's the same validateCapacity() method either way. On the legacy
    // path there is no Offering the caller is already resolving, so this best-effort helper
    // acquires (and discards, matching BillingService/InvoiceService's own established
    // "findByIdForUpdate for its lock side effect alone" pattern) the row lock on whatever
    // canonical Offering Phase 5A's own sync already mapped this legacy item to, if any — a
    // no-op, matching pre-Phase-5C behaviour exactly, only in the (should-not-occur) case no
    // mapping exists yet.
    private void lockMappedOfferingBestEffort(UUID businessId, OfferingResolutionService.LegacyType legacyType, UUID legacyId) {
        UUID offeringId = offeringResolutionService.resolveOfferingId(businessId, legacyType, legacyId);
        if (offeringId != null) {
            offeringRepository.findByIdAndBusinessIdForUpdate(offeringId, businessId);
        }
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

    // ==================== Restaurant-AI-demo phase — package customization discovery/preview ====================
    // Both methods below are pure reads, reusing exactly the same canonical Offering/
    // PackageComponent/Option/SubstitutionRule/PackagePricingService machinery
    // createBooking() itself uses — never a parallel pricing/catalog path, and never anything an
    // AI tool computes itself. Added for AiToolService's getPackageOptions/previewPackagePricing
    // tools, but not AI-specific in any way — any authenticated caller could use these the same
    // way the public booking widget could grow structured substitution UI on top of them later.

    /** Every SELECTION component of a package, its default, and every substitution-rule-backed alternative — exactly what createBooking's own `selections` map may validly name. */
    public PackageOptionsResponse getPackageOptions(UUID businessId, UUID packageId) {
        if (!bookingCutoverStateResolver.useCanonical(businessId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This business doesn't support package customization yet.");
        }
        ResolvedOffering resolved = resolveCanonicalPackage(businessId, packageId, false, true);
        ServicePackage pkg = servicePackageRepository.findByIdAndBusinessId(packageId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Package not found."));

        List<PackageOptionsResponse.PackageComponentOptions> components = new ArrayList<>();
        for (PackageComponent component : packageComponentRepository.findAllByBusinessIdAndOfferingIdOrderByDisplayOrderAsc(businessId, resolved.offeringId())) {
            if (!"SELECTION".equals(component.getComponentKind())) continue; // no QUANTITY components in this phase's package model

            String defaultLabel = null;
            if (component.getDefaultOptionId() != null) {
                defaultLabel = optionRepository.findByIdAndBusinessId(component.getDefaultOptionId(), businessId)
                        .map(Option::getLabel).orElse(null);
            }

            List<PackageOptionsResponse.PackageAlternative> alternatives = new ArrayList<>();
            if (component.getDefaultOptionId() != null) {
                for (SubstitutionRule rule : substitutionRuleRepository.findAllByBusinessIdAndFromOptionId(businessId, component.getDefaultOptionId())) {
                    optionRepository.findByIdAndBusinessId(rule.getToOptionId(), businessId).ifPresent(alt ->
                            alternatives.add(new PackageOptionsResponse.PackageAlternative(alt.getId(), alt.getLabel(), rule.getPriceDelta())));
                }
            }

            components.add(new PackageOptionsResponse.PackageComponentOptions(
                    component.getId(), component.getSlotName(), component.isRequired(),
                    component.getDefaultOptionId(), defaultLabel, alternatives));
        }
        return new PackageOptionsResponse(packageId, pkg.getName(), components);
    }

    /** The exact real total createBooking() itself would charge for this selections/partySize combination — never estimated, never LLM arithmetic. */
    public PackagePricingPreviewResponse previewPackagePricing(UUID businessId, UUID packageId, Map<UUID, UUID> selections, Integer partySize) {
        if (!bookingCutoverStateResolver.useCanonical(businessId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This business doesn't support package pricing preview yet.");
        }
        ResolvedOffering resolved = resolveCanonicalPackage(businessId, packageId, false, true);

        Map<UUID, Set<UUID>> merged = offeringResolutionService.defaultSelections(businessId, resolved.offeringId());
        if (selections != null) {
            for (Map.Entry<UUID, UUID> entry : selections.entrySet()) {
                merged.put(entry.getKey(), Set.of(entry.getValue()));
            }
        }
        PricingResult pricing = packagePricingService.calculate(businessId, resolved.offeringId(), merged, Map.of());
        if (!pricing.valid()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That combination isn't valid: " + String.join(", ", pricing.reasons()));
        }
        if (pricing.manualQuoteRequired() || pricing.finalPrice() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This selection requires a manual quote from staff.");
        }

        int size = (partySize != null && partySize > 0) ? partySize : 1;
        BigDecimal total = pricing.finalPrice().multiply(BigDecimal.valueOf(size)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deposit = total.multiply(new BigDecimal("0.70")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance = total.subtract(deposit).setScale(2, RoundingMode.HALF_UP);

        List<PackagePricingPreviewResponse.PricingLine> lines = new ArrayList<>();
        if (pricing.basePrice().compareTo(BigDecimal.ZERO) != 0 || pricing.adjustments().isEmpty()) {
            lines.add(new PackagePricingPreviewResponse.PricingLine(resolved.offering().getName(), pricing.basePrice()));
        }
        for (PricingAdjustment adjustment : pricing.adjustments()) {
            String label = adjustment.optionId() != null
                    ? optionRepository.findByIdAndBusinessId(adjustment.optionId(), businessId).map(Option::getLabel).orElse("Item")
                    : "Item";
            lines.add(new PackagePricingPreviewResponse.PricingLine(label, adjustment.amount()));
        }
        return new PackagePricingPreviewResponse(packageId, pricing.finalPrice(), size, total, deposit, balance, lines);
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

        // Phase 5C — consulted exactly once, at the top of the resolution block; the entire
        // pricing/eligibility branch below follows from this single result.
        boolean canonical = bookingCutoverStateResolver.useCanonical(businessId);

        String serviceName;
        UUID serviceTypeId;
        UUID serviceCatalogId = null;
        UUID servicePackageId = null;
        BigDecimal price;
        boolean requiresLocation;
        String itemPolicyOverride;
        UUID offeringIdForOrder = null;
        List<PendingLineSnapshot> pendingSnapshots = List.of();
        // Restaurant-AI-demo phase — set only for a canonical package booking with partySize > 1;
        // appended to ServiceOrder.notes below as a plain human-readable line (never a new
        // column/migration). Kept structured enough (a plain "Party size: N" line) that a future
        // reminder feature could parse it back out if it ever needs to, without this phase
        // building any reminder logic itself.
        Integer bookingNotesPartySize = null;

        if (req.packageId() != null) {
            if (canonical) {
                // Capacity-race fix: Offering row locked FIRST, before any capacity read/insert.
                ResolvedOffering resolved = resolveCanonicalPackage(businessId, req.packageId(), true, true);
                Instant requestEnd = req.scheduledAt().plusSeconds(resolved.config().getDurationMinutes() * 60L);
                Instant searchFrom = req.scheduledAt().minusSeconds(resolved.config().getDurationMinutes() * 60L);
                List<ServiceOrder> candidates = serviceOrderRepository.findAllByBusinessIdAndServicePackageIdAndStatusNotAndScheduledAtBetween(
                        businessId, req.packageId(), ServiceOrderStatus.CANCELLED, searchFrom, requestEnd);
                validateCapacity(resolved.config().getDurationMinutes(), resolved.config().getMaxConcurrentBookings(), candidates, req.scheduledAt());

                // Restaurant-AI-demo phase — req.selections() overrides only the components it
                // names; every component it omits keeps the default defaultSelections() already
                // put there, which is exactly PackagePricingService's own "omission implies
                // default" contract (never re-implemented here, just fed through it). A caller
                // (every pre-existing one) that never sets selections gets the untouched defaults
                // map, i.e. today's exact existing behaviour.
                Map<UUID, Set<UUID>> selections = offeringResolutionService.defaultSelections(businessId, resolved.offeringId());
                if (req.selections() != null) {
                    for (Map.Entry<UUID, UUID> entry : req.selections().entrySet()) {
                        selections.put(entry.getKey(), Set.of(entry.getValue()));
                    }
                }
                PricingResult pricing = packagePricingService.calculate(businessId, resolved.offeringId(), selections, Map.of());
                if (!pricing.valid() || pricing.manualQuoteRequired() || pricing.finalPrice() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "This package isn't available for online booking right now.");
                }
                ServicePackage pkg = servicePackageRepository.findByIdAndBusinessId(req.packageId(), businessId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Package not found."));

                // partySize is a plain integer multiplier applied here, in application code —
                // never inside PackagePricingService (which has no concept of it) and never by the
                // AI/LLM. Absent/1 preserves exactly today's existing single-instance behaviour.
                int partySize = (req.partySize() != null && req.partySize() > 0) ? req.partySize() : 1;

                serviceName = resolved.offering().getName();
                serviceTypeId = pkg.getServiceTypeId();
                servicePackageId = pkg.getId();
                price = pricing.finalPrice().multiply(BigDecimal.valueOf(partySize)).setScale(2, RoundingMode.HALF_UP);
                requiresLocation = resolved.config().isRequiresLocation();
                itemPolicyOverride = resolved.config().getPaymentPolicyOverride();
                offeringIdForOrder = resolved.offeringId();
                pendingSnapshots = snapshotsFromPricing(pricing, businessId, resolved.offering().getName(), partySize);
                bookingNotesPartySize = partySize > 1 ? partySize : null;
            } else {
                lockMappedOfferingBestEffort(businessId, OfferingResolutionService.LegacyType.SERVICE_PACKAGE, req.packageId());
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
            }
        } else {
            if (canonical) {
                ResolvedOffering resolved = resolveCanonicalService(businessId, req.serviceCatalogId(), true, true);
                Instant requestEnd = req.scheduledAt().plusSeconds(resolved.config().getDurationMinutes() * 60L);
                Instant searchFrom = req.scheduledAt().minusSeconds(resolved.config().getDurationMinutes() * 60L);
                List<ServiceOrder> candidates = serviceOrderRepository.findAllByBusinessIdAndServiceCatalogIdAndStatusNotAndScheduledAtBetween(
                        businessId, req.serviceCatalogId(), ServiceOrderStatus.CANCELLED, searchFrom, requestEnd);
                validateCapacity(resolved.config().getDurationMinutes(), resolved.config().getMaxConcurrentBookings(), candidates, req.scheduledAt());

                PricingResult pricing = packagePricingService.calculate(businessId, resolved.offeringId(), Map.of(), Map.of());
                if (!pricing.valid() || pricing.finalPrice() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "This service isn't available for online booking right now.");
                }
                ServiceCatalogItem catalogItem = serviceCatalogItemRepository.findByIdAndBusinessId(req.serviceCatalogId(), businessId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service not found."));

                serviceName = resolved.offering().getName();
                serviceTypeId = catalogItem.getServiceTypeId();
                serviceCatalogId = catalogItem.getId();
                price = pricing.finalPrice();
                requiresLocation = resolved.config().isRequiresLocation();
                itemPolicyOverride = resolved.config().getPaymentPolicyOverride();
                offeringIdForOrder = resolved.offeringId();
                pendingSnapshots = snapshotsFromPricing(pricing, businessId, resolved.offering().getName());
            } else {
                lockMappedOfferingBestEffort(businessId, OfferingResolutionService.LegacyType.SERVICE_CATALOG_ITEM, req.serviceCatalogId());
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

        // Phase 4 — the authoritative gate (Revision 4 §5), immediately before persisting. Phase
        // 5C now supplies a real offeringId when canonical (null when legacy, unchanged from
        // before this phase) — no PolicyEngine code changed; this is purely the missing value it
        // already accepted. Zero behaviour change for a business with no BOOKING_CREATE policy
        // configured: see PolicyEngine.evaluateGate's own empty-applicable-set short circuit.
        GateResult gate = policyEngine.evaluateGate(businessId, "BOOKING_CREATE", offeringIdForOrder,
                req.commitmentReference(), "BOOKING", customer.getId());
        if (!gate.allowed()) {
            throw new ApiException(HttpStatus.CONFLICT, String.join(" ", gate.reasons()));
        }

        ServiceOrder order = ServiceOrder.builder()
                .businessId(businessId)
                .serviceTypeId(serviceTypeId)
                .status(ServiceOrderStatus.RECEIVED)
                .customerId(customer.getId())
                .serviceCatalogId(serviceCatalogId)
                .servicePackageId(servicePackageId)
                .offeringId(offeringIdForOrder)
                .notes(withPartySizeNote(req.notes(), bookingNotesPartySize))
                .price(price)
                .scheduledAt(req.scheduledAt())
                .build();
        order = serviceOrderRepository.save(order);
        createServiceOrderItem(businessId, order.getId(), serviceTypeId, serviceCatalogId, serviceName, price);
        // Phase 5C — the immutable historical transaction snapshot (frozen design §2/§6), same
        // transaction as the ServiceOrder/Booking insert below: if this method rolls back for any
        // reason, these rows roll back with it (no snapshot can survive a failed booking).
        if (!pendingSnapshots.isEmpty()) {
            persistLineSnapshots(businessId, order.getId(), pendingSnapshots);
        }

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

        // Phase 4 — step 8 of the frozen gate sequence: complete the commitment now that the
        // real transaction row exists, in the SAME @Transactional method/DB transaction as the
        // gate check and the insert above (Revision 4 §10 — a commitment cannot become COMPLETED
        // for a transaction that was not persisted, because both share one transaction boundary).
        if (req.commitmentReference() != null) {
            policyEngine.linkCommitmentToTransaction(businessId, req.commitmentReference(), "BOOKING", booking.getId());
        }

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

        // Phase 5C — consulted exactly once; the entire pricing/eligibility branch below follows
        // from this single result, mirroring createBooking()'s own discipline exactly.
        boolean canonical = bookingCutoverStateResolver.useCanonical(businessId);

        String serviceName;
        UUID serviceTypeId;
        UUID serviceCatalogId = null;
        UUID servicePackageId = null;
        BigDecimal price;
        boolean requiresLocation;
        UUID offeringIdForOrder = null;
        List<PendingLineSnapshot> pendingSnapshots = List.of();

        if (req.packageId() != null) {
            if (canonical) {
                // Staff path never filters on bookableOnline (fresh source re-confirmed — only
                // .isActive() is enforced today) — enforceBookableOnline=false mirrors that exactly.
                ResolvedOffering resolved = resolveCanonicalPackage(businessId, req.packageId(), true, false);
                Instant requestEnd = req.scheduledAt().plusSeconds(resolved.config().getDurationMinutes() * 60L);
                Instant searchFrom = req.scheduledAt().minusSeconds(resolved.config().getDurationMinutes() * 60L);
                List<ServiceOrder> candidates = serviceOrderRepository.findAllByBusinessIdAndServicePackageIdAndStatusNotAndScheduledAtBetween(
                        businessId, req.packageId(), ServiceOrderStatus.CANCELLED, searchFrom, requestEnd);
                validateCapacity(resolved.config().getDurationMinutes(), resolved.config().getMaxConcurrentBookings(), candidates, req.scheduledAt());

                Map<UUID, Set<UUID>> selections = offeringResolutionService.defaultSelections(businessId, resolved.offeringId());
                PricingResult pricing = packagePricingService.calculate(businessId, resolved.offeringId(), selections, Map.of());
                if (!pricing.valid() || pricing.manualQuoteRequired() || pricing.finalPrice() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "This package isn't available for booking right now.");
                }
                ServicePackage pkg = servicePackageRepository.findByIdAndBusinessId(req.packageId(), businessId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Package not found."));

                serviceName = resolved.offering().getName();
                serviceTypeId = pkg.getServiceTypeId();
                servicePackageId = pkg.getId();
                price = pricing.finalPrice();
                requiresLocation = resolved.config().isRequiresLocation();
                offeringIdForOrder = resolved.offeringId();
                pendingSnapshots = snapshotsFromPricing(pricing, businessId, resolved.offering().getName());
            } else {
                lockMappedOfferingBestEffort(businessId, OfferingResolutionService.LegacyType.SERVICE_PACKAGE, req.packageId());
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
            }
        } else {
            if (canonical) {
                ResolvedOffering resolved = resolveCanonicalService(businessId, req.serviceCatalogId(), true, false);
                Instant requestEnd = req.scheduledAt().plusSeconds(resolved.config().getDurationMinutes() * 60L);
                Instant searchFrom = req.scheduledAt().minusSeconds(resolved.config().getDurationMinutes() * 60L);
                List<ServiceOrder> candidates = serviceOrderRepository.findAllByBusinessIdAndServiceCatalogIdAndStatusNotAndScheduledAtBetween(
                        businessId, req.serviceCatalogId(), ServiceOrderStatus.CANCELLED, searchFrom, requestEnd);
                validateCapacity(resolved.config().getDurationMinutes(), resolved.config().getMaxConcurrentBookings(), candidates, req.scheduledAt());

                PricingResult pricing = packagePricingService.calculate(businessId, resolved.offeringId(), Map.of(), Map.of());
                if (!pricing.valid() || pricing.finalPrice() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "This service isn't available for booking right now.");
                }
                ServiceCatalogItem catalogItem = serviceCatalogItemRepository.findByIdAndBusinessId(req.serviceCatalogId(), businessId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service not found."));

                serviceName = resolved.offering().getName();
                serviceTypeId = catalogItem.getServiceTypeId();
                serviceCatalogId = catalogItem.getId();
                price = pricing.finalPrice();
                requiresLocation = resolved.config().isRequiresLocation();
                offeringIdForOrder = resolved.offeringId();
                pendingSnapshots = snapshotsFromPricing(pricing, businessId, resolved.offering().getName());
            } else {
                lockMappedOfferingBestEffort(businessId, OfferingResolutionService.LegacyType.SERVICE_CATALOG_ITEM, req.serviceCatalogId());
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

        // Phase 4 — the authoritative gate (Revision 4 §5), immediately before persisting. Phase
        // 5C now supplies a real offeringId when canonical, same reasoning as createBooking().
        GateResult gate = policyEngine.evaluateGate(businessId, "STAFF_BOOKING_CREATE", offeringIdForOrder,
                req.commitmentReference(), "BOOKING", resolvedCustomerId);
        if (!gate.allowed()) {
            throw new ApiException(HttpStatus.CONFLICT, String.join(" ", gate.reasons()));
        }

        ServiceOrder order = ServiceOrder.builder()
                .businessId(businessId)
                .serviceTypeId(serviceTypeId)
                .status(ServiceOrderStatus.RECEIVED)
                .customerId(resolvedCustomerId)
                .serviceCatalogId(serviceCatalogId)
                .servicePackageId(servicePackageId)
                .offeringId(offeringIdForOrder)
                .notes(req.notes())
                .price(price)
                .assignedStaffId(req.assignedStaffId())
                .scheduledAt(req.scheduledAt())
                .createdBy(TenantContext.getUserId())
                .build();
        order = serviceOrderRepository.save(order);
        createServiceOrderItem(businessId, order.getId(), serviceTypeId, serviceCatalogId, serviceName, price);
        if (!pendingSnapshots.isEmpty()) {
            persistLineSnapshots(businessId, order.getId(), pendingSnapshots);
        }

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

        // Phase 4 — step 8 of the frozen gate sequence, same transaction as the gate check/insert above.
        if (req.commitmentReference() != null) {
            policyEngine.linkCommitmentToTransaction(businessId, req.commitmentReference(), "BOOKING", booking.getId());
        }

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
