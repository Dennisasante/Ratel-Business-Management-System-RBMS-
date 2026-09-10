package com.ratel.rbms.service;

import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.OfferingBookingConfig;
import com.ratel.rbms.entity.PackageComponent;
import com.ratel.rbms.entity.Policy;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServicePackage;
import com.ratel.rbms.entity.ServicePackageItem;
import com.ratel.rbms.repository.OfferingBookingConfigRepository;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.OptionRepository;
import com.ratel.rbms.repository.PackageComponentRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServicePackageItemRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5C — Stage 1 offline commercial-parity/policy-compatibility
 * verification (frozen design, Revision 4 §1/§3, tightened by the final implementation
 * instruction §19/§20). Read-only: never mutates legacy or canonical data, never opens a
 * PolicyEngine commitment. Scoped to exactly what {@code BookingService} itself would ever
 * surface — active AND bookable-online items only (an inactive/non-bookable item can never
 * produce a customer-facing discrepancy regardless of its canonical mirror's state).
 *
 * <p>Nine commercial-parity dimensions (frozen taxonomy — never an invented parallel one, per the
 * approved instruction "use the appropriate existing availability/configuration mismatch
 * representation" for anything not explicitly named): price, name/label, duration,
 * active/bookable, max concurrency, location requirement, payment/deposit, package contents, and
 * ServiceOrder commercial-field byte-identity (the last is a structural guarantee proven by
 * construction/test, not a live diff here — see {@code BookingServiceCanonicalBookingTest}'s own
 * proof that the canonical creation path writes the exact same {@code price}/{@code serviceName}
 * shape dimensions 1/2 already validate — there is no live canonical ServiceOrder to compare
 * against before a business ever reaches CANONICAL_ENABLED).
 *
 * <p>Policy compatibility (Revision 4 §1) is evaluated separately and never contributes to
 * {@code clean} via widening — only narrowing blocks.
 */
@Service
public class Stage1VerificationService {

    private static final List<String> BOOKING_GATE_ACTIONS = List.of("BOOKING_CREATE", "STAFF_BOOKING_CREATE");

    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final ServicePackageItemRepository servicePackageItemRepository;
    private final OfferingRepository offeringRepository;
    private final OfferingBookingConfigRepository offeringBookingConfigRepository;
    private final PackageComponentRepository packageComponentRepository;
    private final OptionRepository optionRepository;
    private final OfferingResolutionService offeringResolutionService;
    private final PackagePricingService packagePricingService;
    private final PackageContentBackfillService packageContentBackfillService;
    private final PolicyEngine policyEngine;

    public Stage1VerificationService(
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            ServicePackageRepository servicePackageRepository,
            ServicePackageItemRepository servicePackageItemRepository,
            OfferingRepository offeringRepository,
            OfferingBookingConfigRepository offeringBookingConfigRepository,
            PackageComponentRepository packageComponentRepository,
            OptionRepository optionRepository,
            OfferingResolutionService offeringResolutionService,
            PackagePricingService packagePricingService,
            PackageContentBackfillService packageContentBackfillService,
            PolicyEngine policyEngine
    ) {
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.servicePackageRepository = servicePackageRepository;
        this.servicePackageItemRepository = servicePackageItemRepository;
        this.offeringRepository = offeringRepository;
        this.offeringBookingConfigRepository = offeringBookingConfigRepository;
        this.packageComponentRepository = packageComponentRepository;
        this.optionRepository = optionRepository;
        this.offeringResolutionService = offeringResolutionService;
        this.packagePricingService = packagePricingService;
        this.packageContentBackfillService = packageContentBackfillService;
        this.policyEngine = policyEngine;
    }

    public VerificationResult verifyBusiness(UUID businessId) {
        List<ParityMismatch> mismatches = new ArrayList<>();
        List<String> widenings = new ArrayList<>();

        for (ServiceCatalogItem item : serviceCatalogItemRepository.findAllByBusinessIdAndActiveTrueAndBookableOnlineTrueOrderByNameAsc(businessId)) {
            verifyServiceItem(businessId, item, mismatches, widenings);
        }
        for (ServicePackage pkg : servicePackageRepository.findAllByBusinessIdAndActiveTrueAndBookableOnlineTrueOrderByNameAsc(businessId)) {
            verifyPackage(businessId, pkg, mismatches, widenings);
        }

        return new VerificationResult(businessId, mismatches, widenings);
    }

    private void verifyServiceItem(UUID businessId, ServiceCatalogItem item, List<ParityMismatch> mismatches, List<String> widenings) {
        UUID offeringId = offeringResolutionService.resolveOfferingId(
                businessId, OfferingResolutionService.LegacyType.SERVICE_CATALOG_ITEM, item.getId());
        if (offeringId == null) {
            mismatches.add(new ParityMismatch(ParityMismatch.AVAILABILITY_FLAG_MISMATCH, "SERVICE_CATALOG_ITEM", item.getId(),
                    "No canonical Offering mapping exists — cannot become canonically available."));
            return;
        }
        Offering offering = offeringRepository.findByIdAndBusinessId(offeringId, businessId).orElse(null);
        OfferingBookingConfig config = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offeringId, businessId).orElse(null);
        if (offering == null || config == null) {
            mismatches.add(new ParityMismatch(ParityMismatch.AVAILABILITY_FLAG_MISMATCH, "SERVICE_CATALOG_ITEM", item.getId(),
                    "Offering mapping exists but Offering/OfferingBookingConfig row is missing."));
            return;
        }

        if (item.getPrice().compareTo(offering.getBasePrice()) != 0) {
            mismatches.add(new ParityMismatch(ParityMismatch.PRICE_MISMATCH, "SERVICE_CATALOG_ITEM", item.getId(),
                    "legacy=" + item.getPrice() + " canonical=" + offering.getBasePrice()));
        }
        if (!item.getName().equals(offering.getName())) {
            mismatches.add(new ParityMismatch(ParityMismatch.LABEL_MISMATCH, "SERVICE_CATALOG_ITEM", item.getId(),
                    "legacy=\"" + item.getName() + "\" canonical=\"" + offering.getName() + "\""));
        }
        if (item.getDurationMinutes() != config.getDurationMinutes()) {
            mismatches.add(new ParityMismatch(ParityMismatch.DURATION_MISMATCH, "SERVICE_CATALOG_ITEM", item.getId(),
                    "legacy=" + item.getDurationMinutes() + " canonical=" + config.getDurationMinutes()));
        }
        if (item.isActive() != offering.isActive() || item.isBookableOnline() != config.isBookableOnline()) {
            mismatches.add(new ParityMismatch(ParityMismatch.AVAILABILITY_FLAG_MISMATCH, "SERVICE_CATALOG_ITEM", item.getId(),
                    "legacy(active=" + item.isActive() + ",bookableOnline=" + item.isBookableOnline() + ") canonical(active="
                            + offering.isActive() + ",bookableOnline=" + config.isBookableOnline() + ")"));
        }
        if (item.getMaxConcurrentBookings() != config.getMaxConcurrentBookings()) {
            mismatches.add(new ParityMismatch(ParityMismatch.AVAILABILITY_FLAG_MISMATCH, "SERVICE_CATALOG_ITEM", item.getId(),
                    "max concurrency legacy=" + item.getMaxConcurrentBookings() + " canonical=" + config.getMaxConcurrentBookings()));
        }
        if (item.isRequiresLocation() != config.isRequiresLocation()) {
            mismatches.add(new ParityMismatch(ParityMismatch.AVAILABILITY_FLAG_MISMATCH, "SERVICE_CATALOG_ITEM", item.getId(),
                    "requiresLocation legacy=" + item.isRequiresLocation() + " canonical=" + config.isRequiresLocation()));
        }
        if (!Objects.equals(blankToNull(item.getPaymentPolicyOverride()), blankToNull(config.getPaymentPolicyOverride()))) {
            mismatches.add(new ParityMismatch(ParityMismatch.PAYMENT_POLICY_MISMATCH, "SERVICE_CATALOG_ITEM", item.getId(),
                    "legacy=" + item.getPaymentPolicyOverride() + " canonical=" + config.getPaymentPolicyOverride()));
        }

        verifyPolicyCompatibility(businessId, offeringId, "SERVICE_CATALOG_ITEM", item.getId(), mismatches, widenings);
    }

    private void verifyPackage(UUID businessId, ServicePackage pkg, List<ParityMismatch> mismatches, List<String> widenings) {
        UUID offeringId = offeringResolutionService.resolveOfferingId(
                businessId, OfferingResolutionService.LegacyType.SERVICE_PACKAGE, pkg.getId());
        if (offeringId == null) {
            mismatches.add(new ParityMismatch(ParityMismatch.AVAILABILITY_FLAG_MISMATCH, "SERVICE_PACKAGE", pkg.getId(),
                    "No canonical Offering mapping exists — cannot become canonically available."));
            return;
        }
        Offering offering = offeringRepository.findByIdAndBusinessId(offeringId, businessId).orElse(null);
        OfferingBookingConfig config = offeringBookingConfigRepository.findByOfferingIdAndBusinessId(offeringId, businessId).orElse(null);
        if (offering == null || config == null) {
            mismatches.add(new ParityMismatch(ParityMismatch.AVAILABILITY_FLAG_MISMATCH, "SERVICE_PACKAGE", pkg.getId(),
                    "Offering mapping exists but Offering/OfferingBookingConfig row is missing."));
            return;
        }

        // Orphan canonical components block cutover outright (Revision 4 §3/§6) — checked first,
        // since a stale component makes the contents/price comparison below meaningless.
        List<PackageComponent> orphans = packageContentBackfillService.findOrphanComponents(businessId, offeringId);
        if (!orphans.isEmpty()) {
            mismatches.add(new ParityMismatch(ParityMismatch.CONTENTS_MISMATCH, "SERVICE_PACKAGE", pkg.getId(),
                    orphans.size() + " orphan canonical component(s) with no live source ServicePackageItem."));
        }

        Map<UUID, Set<UUID>> selections = offeringResolutionService.defaultSelections(businessId, offeringId);
        PricingResult pricing = packagePricingService.calculate(businessId, offeringId, selections, Map.of());
        if (!pricing.valid() || pricing.manualQuoteRequired() || pricing.finalPrice() == null) {
            mismatches.add(new ParityMismatch(ParityMismatch.PRICE_MISMATCH, "SERVICE_PACKAGE", pkg.getId(),
                    "Canonical default-selection calculation is not a clean chargeable price: reasons="
                            + pricing.reasons() + " manualQuoteRequired=" + pricing.manualQuoteRequired()));
        } else if (pkg.getPrice().compareTo(pricing.finalPrice()) != 0) {
            mismatches.add(new ParityMismatch(ParityMismatch.PRICE_MISMATCH, "SERVICE_PACKAGE", pkg.getId(),
                    "legacy=" + pkg.getPrice() + " canonical=" + pricing.finalPrice()));
        }

        if (!pkg.getName().equals(offering.getName())) {
            mismatches.add(new ParityMismatch(ParityMismatch.LABEL_MISMATCH, "SERVICE_PACKAGE", pkg.getId(),
                    "legacy=\"" + pkg.getName() + "\" canonical=\"" + offering.getName() + "\""));
        }
        if (pkg.getDurationMinutes() != config.getDurationMinutes()) {
            mismatches.add(new ParityMismatch(ParityMismatch.DURATION_MISMATCH, "SERVICE_PACKAGE", pkg.getId(),
                    "legacy=" + pkg.getDurationMinutes() + " canonical=" + config.getDurationMinutes()));
        }
        if (pkg.isActive() != offering.isActive() || pkg.isBookableOnline() != config.isBookableOnline()) {
            mismatches.add(new ParityMismatch(ParityMismatch.AVAILABILITY_FLAG_MISMATCH, "SERVICE_PACKAGE", pkg.getId(),
                    "legacy(active=" + pkg.isActive() + ",bookableOnline=" + pkg.isBookableOnline() + ") canonical(active="
                            + offering.isActive() + ",bookableOnline=" + config.isBookableOnline() + ")"));
        }
        if (pkg.getMaxConcurrentBookings() != config.getMaxConcurrentBookings()) {
            mismatches.add(new ParityMismatch(ParityMismatch.AVAILABILITY_FLAG_MISMATCH, "SERVICE_PACKAGE", pkg.getId(),
                    "max concurrency legacy=" + pkg.getMaxConcurrentBookings() + " canonical=" + config.getMaxConcurrentBookings()));
        }
        // Legacy hardcodes false for every package (BookingService.createBooking, confirmed by
        // fresh source read — ServicePackage itself has no requiresLocation column at all).
        if (config.isRequiresLocation()) {
            mismatches.add(new ParityMismatch(ParityMismatch.AVAILABILITY_FLAG_MISMATCH, "SERVICE_PACKAGE", pkg.getId(),
                    "legacy requiresLocation is always false for packages; canonical=" + config.isRequiresLocation()));
        }
        if (!Objects.equals(blankToNull(pkg.getPaymentPolicyOverride()), blankToNull(config.getPaymentPolicyOverride()))) {
            mismatches.add(new ParityMismatch(ParityMismatch.PAYMENT_POLICY_MISMATCH, "SERVICE_PACKAGE", pkg.getId(),
                    "legacy=" + pkg.getPaymentPolicyOverride() + " canonical=" + config.getPaymentPolicyOverride()));
        }

        verifyPackageContents(businessId, pkg, offeringId, mismatches);
        verifyPolicyCompatibility(businessId, offeringId, "SERVICE_PACKAGE", pkg.getId(), mismatches, widenings);
    }

    /**
     * Dimension 8 — proves the backfill reproduces legacy's own included-item DISPLAY exactly
     * (label set equality, order-independent per the frozen comparator), not merely the total
     * price. Mirrors {@code BookingService.includedItemLabels}'s exact derivation.
     */
    private void verifyPackageContents(UUID businessId, ServicePackage pkg, UUID offeringId, List<ParityMismatch> mismatches) {
        List<String> legacyLabels = new ArrayList<>();
        for (ServicePackageItem item : servicePackageItemRepository.findAllByPackageId(pkg.getId())) {
            String name = serviceCatalogItemRepository.findByIdAndBusinessId(item.getServiceCatalogId(), businessId)
                    .map(ServiceCatalogItem::getName).orElse("Item");
            legacyLabels.add(item.getQuantity() > 1 ? item.getQuantity() + "x " + name : name);
        }

        List<String> canonicalLabels = new ArrayList<>();
        for (PackageComponent component : packageComponentRepository.findAllByBusinessIdAndOfferingIdOrderByDisplayOrderAsc(businessId, offeringId)) {
            if (component.getDefaultOptionId() == null) continue;
            Optional<String> label = optionRepository.findByIdAndBusinessId(component.getDefaultOptionId(), businessId).map(o -> o.getLabel());
            label.ifPresent(canonicalLabels::add);
        }

        if (legacyLabels.size() != canonicalLabels.size() || !sorted(legacyLabels).equals(sorted(canonicalLabels))) {
            mismatches.add(new ParityMismatch(ParityMismatch.CONTENTS_MISMATCH, "SERVICE_PACKAGE", pkg.getId(),
                    "legacy=" + sorted(legacyLabels) + " canonical=" + sorted(canonicalLabels)));
        }
    }

    private static List<String> sorted(List<String> in) {
        List<String> copy = new ArrayList<>(in);
        copy.sort(Comparator.naturalOrder());
        return copy;
    }

    /**
     * Policy compatibility (Revision 4 §1, kept separate from commercial parity): canonical
     * (offering-aware) applicable-policy matching must never resolve to FEWER policies than
     * legacy's offering-blind matching, for either booking-gate action. Fewer =
     * {@code POLICY_SCOPE_NARROWED} (blocking). More = informational widening only.
     */
    private void verifyPolicyCompatibility(UUID businessId, UUID offeringId, String subjectType, UUID subjectId,
                                            List<ParityMismatch> mismatches, List<String> widenings) {
        for (String action : BOOKING_GATE_ACTIONS) {
            Set<UUID> legacyIds = idsOf(policyEngine.applicablePolicies(businessId, action, null));
            Set<UUID> canonicalIds = idsOf(policyEngine.applicablePolicies(businessId, action, offeringId));

            Set<UUID> narrowed = new HashSet<>(legacyIds);
            narrowed.removeAll(canonicalIds);
            if (!narrowed.isEmpty()) {
                mismatches.add(new ParityMismatch(ParityMismatch.POLICY_SCOPE_NARROWED, subjectType, subjectId,
                        "action=" + action + " lost policy id(s)=" + narrowed));
            }

            Set<UUID> widened = new HashSet<>(canonicalIds);
            widened.removeAll(legacyIds);
            if (!widened.isEmpty()) {
                widenings.add(subjectType + " " + subjectId + " action=" + action + " gained policy id(s)=" + widened);
            }
        }
    }

    private static Set<UUID> idsOf(List<Policy> policies) {
        Set<UUID> ids = new HashSet<>();
        for (Policy p : policies) ids.add(p.getId());
        return ids;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
