package com.ratel.rbms.service;

import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.PackageComponent;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.PackageComponentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5A — the ONE approved way to translate a legacy item into its
 * canonical {@link Offering} id. Every future phase that needs "which Offering does this legacy
 * item correspond to" (starting with a real {@code offeringId} for
 * {@code PolicyEngine.evaluateGate}, Phase 5C+) must call this class, never re-implement the
 * lookup — that is what guarantees "same legacy item → same canonical Offering → same
 * offeringId" regardless of which transaction path resolves it.
 *
 * <p>Purely a read-only resolver: never creates an Offering, never guesses by name, never falls
 * back to a heuristic. A {@code null} result means "no mapping exists" — a real, legitimate
 * answer (e.g. before backfill runs, or for a legacy item created before its synchronized write
 * path existed), not an error.
 *
 * <p>Deliberately NOT wired into any transaction-creating service in this phase — Phase 5A
 * establishes the mapping and this resolver; nothing yet calls it from a live path. See the
 * Phase 5A specification's own explicit scope boundary.
 */
@Service
public class OfferingResolutionService {

    public enum LegacyType {
        SERVICE_CATALOG_ITEM,
        SERVICE_PACKAGE
    }

    private final OfferingRepository offeringRepository;
    private final PackageComponentRepository packageComponentRepository;

    public OfferingResolutionService(OfferingRepository offeringRepository,
                                      PackageComponentRepository packageComponentRepository) {
        this.offeringRepository = offeringRepository;
        this.packageComponentRepository = packageComponentRepository;
    }

    /**
     * @return the linked Offering's id, or {@code null} if no mapping exists for this legacy item.
     */
    public UUID resolveOfferingId(UUID businessId, LegacyType legacyType, UUID legacyId) {
        if (businessId == null || legacyType == null || legacyId == null) {
            throw new IllegalArgumentException("businessId, legacyType, and legacyId are all required.");
        }
        return switch (legacyType) {
            case SERVICE_CATALOG_ITEM -> offeringRepository
                    .findByBusinessIdAndLegacyServiceCatalogId(businessId, legacyId)
                    .map(Offering::getId).orElse(null);
            case SERVICE_PACKAGE -> offeringRepository
                    .findByBusinessIdAndLegacyServicePackageId(businessId, legacyId)
                    .map(Offering::getId).orElse(null);
        };
    }

    /**
     * Phase 5A's "Option.offering_id may reference only SERVICE Offerings" rule — deliberately
     * enforced here at the service layer, not the database (a cross-table type check would need
     * a trigger, and this schema has stayed trigger-free for this exact class of invariant since
     * Phase 2 — see {@code PackageComponent}'s own class comment). This is the one place that
     * rule is checked; nothing else should re-implement it.
     */
    public void requireServiceType(Offering offering) {
        if (!"SERVICE".equals(offering.getType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "An Option may only reference a SERVICE Offering (this one is " + offering.getType() + ").");
        }
    }

    /**
     * Talia Unified Platform, Phase 5C — the package-content-resolution boundary (Revision 4 §7):
     * "every required component defaults to its own default option" is the ONLY selection shape
     * Phase 5C's own booking API ever needs, since today's {@code CreateBookingRequest}/
     * {@code CreateStaffBookingRequest} expose no customer-facing option-selection fields at all.
     * Deliberately lives here, not on {@link PackagePricingService} — that service stays a pure
     * calculator over whatever selections it is handed (Phase 5B's own frozen contract, unchanged
     * by this phase); resolving WHAT those selections are is a separate concern, one step above
     * pricing in the call order (Offering → content resolution → PackagePricingService →
     * PricingResult).
     *
     * <p>A required component with no default (not producible by
     * {@code service/PackageContentBackfillService.java}'s own backfill, which always sets one) is
     * simply omitted here — {@code PackagePricingService.calculate} then correctly reports
     * {@code SLOT_REQUIRED} for it, exactly as if the caller had explicitly selected nothing.
     */
    public Map<UUID, Set<UUID>> defaultSelections(UUID businessId, UUID offeringId) {
        List<PackageComponent> components = packageComponentRepository.findAllByBusinessIdAndOfferingId(businessId, offeringId);
        Map<UUID, Set<UUID>> selections = new HashMap<>();
        for (PackageComponent component : components) {
            if ("SELECTION".equals(component.getComponentKind()) && component.getDefaultOptionId() != null) {
                selections.put(component.getId(), Set.of(component.getDefaultOptionId()));
            }
        }
        return selections;
    }
}
