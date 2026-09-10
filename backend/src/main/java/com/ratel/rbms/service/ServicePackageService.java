package com.ratel.rbms.service;

import com.ratel.rbms.dto.ServicePackageItemRequest;
import com.ratel.rbms.dto.ServicePackageItemResponse;
import com.ratel.rbms.dto.ServicePackageRequest;
import com.ratel.rbms.dto.ServicePackageResponse;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServicePackage;
import com.ratel.rbms.entity.ServicePackageItem;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServicePackageItemRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A bundle of real service_catalog items sold/booked as one atomic unit (e.g.
 * a bridal package). Mirrors ServiceCatalogService's shape for the package
 * itself, and CustomItemAttributeService's "delete then rebuild" pattern for
 * the component item list, which is edited as a whole set on every save.
 *
 * <p>Phase 5C targeted correction: every mutation that can affect the exact population
 * {@code Stage1VerificationService} trusts (an {@code active && bookableOnline} package —
 * confirmed against that class's own query, not assumed) invalidates the owning business's
 * canonical cutover state via the existing {@link BookingCutoverStateResolver#markInvalid},
 * IN THE SAME transaction as the legacy write. {@code update()}'s own delete-then-rebuild of
 * every {@code ServicePackageItem} row (confirmed unconditional, even for a price-only edit)
 * is exactly the mutation that can orphan previously-backfilled {@code PackageComponent} rows;
 * {@code create()} can introduce a new active+bookableOnline package with zero backfilled
 * components; {@code setActive(true)} can reintroduce a previously-stale package into the
 * verified surface. Deactivating a package only ever REMOVES it from that surface, so it needs
 * no invalidation. No new cutover state, no new migration, no independent canonical write —
 * this reuses the resolver's own existing, unmodified transition logic.
 */
@Service
public class ServicePackageService {

    private static final Set<String> VALID_POLICY_OVERRIDES = Set.of("NONE", "DEPOSIT", "FULL");

    private final ServicePackageRepository servicePackageRepository;
    private final ServicePackageItemRepository servicePackageItemRepository;
    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final ServiceTypeService serviceTypeService;
    private final ActivityLogService activityLogService;
    private final OfferingSyncService offeringSyncService;
    private final BookingCutoverStateResolver bookingCutoverStateResolver;

    public ServicePackageService(
            ServicePackageRepository servicePackageRepository,
            ServicePackageItemRepository servicePackageItemRepository,
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            ServiceTypeService serviceTypeService,
            ActivityLogService activityLogService,
            OfferingSyncService offeringSyncService,
            BookingCutoverStateResolver bookingCutoverStateResolver
    ) {
        this.servicePackageRepository = servicePackageRepository;
        this.servicePackageItemRepository = servicePackageItemRepository;
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.serviceTypeService = serviceTypeService;
        this.activityLogService = activityLogService;
        this.offeringSyncService = offeringSyncService;
        this.bookingCutoverStateResolver = bookingCutoverStateResolver;
    }

    // The single reusable predicate for "does this package currently belong to the population
    // Stage1VerificationService/BookingService's canonical listing actually trust" — confirmed
    // against Stage1VerificationService.verifyBusiness's own
    // findAllByBusinessIdAndActiveTrueAndBookableOnlineTrueOrderByNameAsc query. Anything outside
    // this population is invisible to verification and can never be canonically booked either, so
    // its own staleness is inert and needs no invalidation (Revision 4/targeted-correction §6/§13
    // — precise, not indiscriminate).
    private void invalidateIfCanonicallyTrusted(ServicePackage pkg, String reason) {
        if (pkg.isActive() && pkg.isBookableOnline()) {
            bookingCutoverStateResolver.markInvalid(pkg.getBusinessId(), List.of(reason));
        }
    }

    public List<ServicePackageResponse> list() {
        UUID businessId = TenantContext.getBusinessId();
        return servicePackageRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ServicePackage getOwned(UUID id) {
        return servicePackageRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Package not found."));
    }

    @Transactional
    public ServicePackageResponse create(ServicePackageRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        ServiceType type = serviceTypeService.getOwned(req.serviceTypeId());

        ServicePackage pkg = ServicePackage.builder()
                .businessId(businessId)
                .serviceTypeId(type.getId())
                .name(req.name())
                .description(blankToNull(req.description()))
                .price(req.price())
                .bookableOnline(req.bookableOnline() != null && req.bookableOnline())
                .durationMinutes(req.durationMinutes() != null ? req.durationMinutes() : 60)
                .maxConcurrentBookings(req.maxConcurrentBookings() != null ? req.maxConcurrentBookings() : 1)
                .paymentPolicyOverride(normalizePolicyOverride(req.paymentPolicyOverride()))
                .build();
        pkg = servicePackageRepository.save(pkg);
        // Sync the package's own scalar fields as soon as they're known, before the (separately
        // validated) item list — keeps the canonical write adjacent to the legacy write it
        // mirrors, both still inside this one @Transactional method with saveItems() below.
        offeringSyncService.syncServicePackage(pkg);
        saveItems(pkg.getId(), req.items());
        // A brand-new active+bookableOnline package has an Offering (just synced above) but ZERO
        // backfilled PackageComponent rows — not yet a valid canonical representation. If this
        // business's cutover state currently trusts its own canonical data (VERIFIED or later),
        // that trust no longer holds until the package backfill runs and verification re-passes.
        invalidateIfCanonicallyTrusted(pkg, "New package \"" + pkg.getName() + "\" created — no canonical package-content backfill exists for it yet.");
        activityLogService.log("Added package \"" + pkg.getName() + "\"", "SERVICE_PACKAGE", pkg.getId());
        return toResponse(pkg);
    }

    @Transactional
    public ServicePackageResponse update(UUID id, ServicePackageRequest req) {
        ServicePackage pkg = getOwned(id);
        ServiceType type = serviceTypeService.getOwned(req.serviceTypeId());

        pkg.setServiceTypeId(type.getId());
        pkg.setName(req.name());
        pkg.setDescription(blankToNull(req.description()));
        pkg.setPrice(req.price());
        if (req.bookableOnline() != null) pkg.setBookableOnline(req.bookableOnline());
        if (req.durationMinutes() != null) pkg.setDurationMinutes(req.durationMinutes());
        if (req.maxConcurrentBookings() != null) pkg.setMaxConcurrentBookings(req.maxConcurrentBookings());
        pkg.setPaymentPolicyOverride(normalizePolicyOverride(req.paymentPolicyOverride()));
        pkg = servicePackageRepository.save(pkg);
        offeringSyncService.syncServicePackage(pkg);
        // Invalidate BEFORE touching the item rows — every edit unconditionally replaces every
        // ServicePackageItem row with brand-new ids (confirmed by source re-read, including a
        // price-only edit), orphaning any previously-backfilled PackageComponent mapping, so the
        // safety transition belongs to the edit itself, not conditionally on the item-rewrite step
        // succeeding. Same reused invalidation as create()/setActive() — not duplicated logic.
        // Still the SAME transaction as everything below: if saveItems() fails afterward, this
        // write rolls back with it, exactly like every other write in this method.
        invalidateIfCanonicallyTrusted(pkg, "Package \"" + pkg.getName() + "\" edited — its legacy item rows were replaced, orphaning any previously-backfilled canonical mapping.");

        servicePackageItemRepository.deleteAllByPackageId(pkg.getId());
        saveItems(pkg.getId(), req.items());

        return toResponse(pkg);
    }

    // Blank/null means "use the business default" (stored as null); anything
    // else must be one of the real policy values.
    private String normalizePolicyOverride(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase();
        if (!VALID_POLICY_OVERRIDES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment policy override must be NONE, DEPOSIT, or FULL.");
        }
        return normalized;
    }

    @Transactional
    public ServicePackageResponse setActive(UUID id, boolean active) {
        ServicePackage pkg = getOwned(id);
        pkg.setActive(active);
        pkg = servicePackageRepository.save(pkg);
        offeringSyncService.syncServicePackage(pkg);
        // Deactivating only ever REMOVES a package from the population verification/canonical
        // booking trust (Stage1VerificationService filters on active=true too) — strictly safe,
        // never needs invalidation. Reactivating can reintroduce a package whose canonical mapping
        // went stale while it sat inactive (e.g. edited, or never backfilled in the first place) —
        // same check as create()/update(), reused, not duplicated.
        if (active) {
            invalidateIfCanonicallyTrusted(pkg, "Package \"" + pkg.getName() + "\" reactivated — its canonical mapping may have gone stale while inactive.");
        }
        activityLogService.log(
                (active ? "Reactivated" : "Archived") + " package \"" + pkg.getName() + "\"",
                "SERVICE_PACKAGE", pkg.getId()
        );
        return toResponse(pkg);
    }

    private void saveItems(UUID packageId, List<ServicePackageItemRequest> items) {
        UUID businessId = TenantContext.getBusinessId();
        for (ServicePackageItemRequest itemReq : items) {
            ServiceCatalogItem catalogItem = serviceCatalogItemRepository.findByIdAndBusinessId(itemReq.serviceCatalogId(), businessId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "One of the selected services doesn't exist."));
            servicePackageItemRepository.save(ServicePackageItem.builder()
                    .packageId(packageId)
                    .serviceCatalogId(catalogItem.getId())
                    .quantity(itemReq.quantity() != null && itemReq.quantity() > 0 ? itemReq.quantity() : 1)
                    .build());
        }
    }

    private ServicePackageResponse toResponse(ServicePackage pkg) {
        String typeName = resolveTypeNameOrNull(pkg.getServiceTypeId());
        List<ServicePackageItemResponse> items = servicePackageItemRepository.findAllByPackageId(pkg.getId()).stream()
                .map(item -> new ServicePackageItemResponse(
                        item.getId(),
                        item.getServiceCatalogId(),
                        resolveCatalogNameOrNull(item.getServiceCatalogId()),
                        item.getQuantity()
                ))
                .toList();
        return ServicePackageResponse.from(pkg, typeName, items);
    }

    private String resolveTypeNameOrNull(UUID typeId) {
        try {
            return serviceTypeService.getOwned(typeId).getName();
        } catch (ApiException e) {
            return null;
        }
    }

    private String resolveCatalogNameOrNull(UUID catalogItemId) {
        return serviceCatalogItemRepository.findByIdAndBusinessId(catalogItemId, TenantContext.getBusinessId())
                .map(ServiceCatalogItem::getName)
                .orElse(null);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
