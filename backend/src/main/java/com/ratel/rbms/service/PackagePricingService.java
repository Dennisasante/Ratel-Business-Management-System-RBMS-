package com.ratel.rbms.service;

import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.Option;
import com.ratel.rbms.entity.PackageComponent;
import com.ratel.rbms.entity.QuantityRule;
import com.ratel.rbms.entity.SubstitutionRule;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.OptionRepository;
import com.ratel.rbms.repository.PackageComponentRepository;
import com.ratel.rbms.repository.QuantityRuleRepository;
import com.ratel.rbms.repository.SubstitutionRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Talia Unified Platform, Phase 5B — the deterministic pricing/rules engine (frozen
 * specification, Revision 2). Purely a read-only calculator: touches only
 * {@code Offering}/{@code PackageComponent}/{@code Option}/{@code SubstitutionRule}/
 * {@code QuantityRule}, tenant-scoped throughout, no side effects, no write to any table, no read
 * of any transaction/commitment/policy table. Two calls with identical input and identical
 * current catalog state always produce identical output.
 *
 * <p>Deliberately not wired into any transaction-creating service, {@code PolicyEngine}, or AI
 * tool in this phase — see the frozen specification's own scope boundary. A future phase calls
 * this and snapshots its output onto a real transaction line item; this class never does that
 * itself, and has no concept of "as of a past date" — it always prices against the catalog as it
 * exists right now.
 */
@Service
public class PackagePricingService {

    private static final String SELECTION = "SELECTION";
    private static final String QUANTITY = "QUANTITY";

    private final OfferingRepository offeringRepository;
    private final PackageComponentRepository packageComponentRepository;
    private final OptionRepository optionRepository;
    private final SubstitutionRuleRepository substitutionRuleRepository;
    private final QuantityRuleRepository quantityRuleRepository;

    public PackagePricingService(
            OfferingRepository offeringRepository,
            PackageComponentRepository packageComponentRepository,
            OptionRepository optionRepository,
            SubstitutionRuleRepository substitutionRuleRepository,
            QuantityRuleRepository quantityRuleRepository
    ) {
        this.offeringRepository = offeringRepository;
        this.packageComponentRepository = packageComponentRepository;
        this.optionRepository = optionRepository;
        this.substitutionRuleRepository = substitutionRuleRepository;
        this.quantityRuleRepository = quantityRuleRepository;
    }

    /**
     * @param selections one entry per SELECTION component the caller is choosing for — option
     *                    ids must belong to that component. A component omitted entirely is
     *                    treated as "nothing explicitly selected" (see the defaulted/no-default
     *                    handling below).
     * @param quantities  one entry per QUANTITY component the caller is submitting a value for.
     *                    A component omitted entirely is treated as "no quantity submitted."
     */
    public PricingResult calculate(UUID businessId, UUID offeringId,
                                    Map<UUID, Set<UUID>> selections, Map<UUID, Integer> quantities) {
        Offering offering = offeringRepository.findByIdAndBusinessId(offeringId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offering not found."));

        List<PackageComponent> components = packageComponentRepository.findAllByBusinessIdAndOfferingId(businessId, offeringId);
        Map<UUID, PackageComponent> componentsById = new HashMap<>();
        for (PackageComponent component : components) {
            componentsById.put(component.getId(), component);
        }

        List<String> reasons = new ArrayList<>();
        List<PricingAdjustment> adjustments = new ArrayList<>();
        AtomicBoolean manualQuoteRequired = new AtomicBoolean(false);

        // Structural check first: every caller-supplied key must actually belong to this
        // Offering — never trust an id the caller happened to pass, even before touching pricing.
        for (UUID componentId : selections.keySet()) {
            if (!componentsById.containsKey(componentId)) {
                reasons.add("COMPONENT_NOT_IN_OFFERING");
            }
        }
        for (UUID componentId : quantities.keySet()) {
            if (!componentsById.containsKey(componentId)) {
                reasons.add("COMPONENT_NOT_IN_OFFERING");
            }
        }
        if (!reasons.isEmpty()) {
            return new PricingResult(false, reasons, offering.getBasePrice(), adjustments, null, false);
        }

        // (fromOptionId, toOptionId) usage within THIS calculation only — Revision 2 Clarification 4.
        Map<List<UUID>, Integer> substitutionUsage = new HashMap<>();

        for (PackageComponent component : components) {
            switch (component.getComponentKind()) {
                case SELECTION -> processSelectionComponent(businessId, component,
                        selections.getOrDefault(component.getId(), Set.of()),
                        reasons, adjustments, substitutionUsage, manualQuoteRequired);
                case QUANTITY -> processQuantityComponent(businessId, component,
                        quantities.get(component.getId()), reasons, adjustments);
                default -> reasons.add("CONFIGURATION_INCONSISTENT"); // unreachable given chk_package_components_kind — defensive only
            }
        }

        boolean valid = reasons.isEmpty();
        BigDecimal subtotal = offering.getBasePrice();
        for (PricingAdjustment adjustment : adjustments) {
            subtotal = subtotal.add(adjustment.amount());
        }
        // Rounded exactly once, at the end — never per intermediate step (Revision, §7).
        BigDecimal finalPrice = (valid && !manualQuoteRequired.get()) ? subtotal.setScale(2, RoundingMode.HALF_UP) : null;

        return new PricingResult(valid, reasons, offering.getBasePrice(), adjustments, finalPrice, manualQuoteRequired.get());
    }

    private void processSelectionComponent(UUID businessId, PackageComponent component, Set<UUID> explicitlySelected,
                                            List<String> reasons, List<PricingAdjustment> adjustments,
                                            Map<List<UUID>, Integer> substitutionUsage, AtomicBoolean manualQuoteRequired) {
        // A QuantityRule should never exist on a SELECTION-kind component — a real, structurally
        // possible catalog defect nothing else currently prevents.
        if (quantityRuleRepository.findByBusinessIdAndComponentId(businessId, component.getId()).isPresent()) {
            reasons.add("CONFIGURATION_INCONSISTENT");
            return;
        }

        UUID defaultOptionId = component.getDefaultOptionId();
        Option defaultOption = null;
        if (defaultOptionId != null) {
            defaultOption = optionRepository.findByIdAndBusinessId(defaultOptionId, businessId).orElse(null);
            if (defaultOption == null || !component.getId().equals(defaultOption.getComponentId())) {
                // The default references an option from another component/business, or a
                // dangling id — a catalog defect, not a customer-facing selection error.
                reasons.add("CONFIGURATION_INCONSISTENT");
                return;
            }

            // Proactive catalog-integrity check (found necessary on re-verification): nothing in
            // the schema stops a SubstitutionRule's "to" side from pointing at an option that
            // belongs to a DIFFERENT component than its "from" side — the per-selection lookup
            // used below (findByBusinessIdAndFromOptionIdAndToOptionId) can never observe this,
            // because both its search keys are already pre-validated to belong to THIS component
            // by the time it runs, so a malformed rule would simply never be found rather than be
            // found-and-misapplied. That means a malformed rule could otherwise sit undetected in
            // the catalog indefinitely. Verified here instead, independent of what any specific
            // customer selects, exactly once per calculation against this component — every rule
            // rooted at this component's own default must resolve to an option in this same
            // component, or the whole component is refused as CONFIGURATION_INCONSISTENT.
            for (SubstitutionRule rule : substitutionRuleRepository.findAllByBusinessIdAndFromOptionId(businessId, defaultOptionId)) {
                Option ruleTarget = optionRepository.findByIdAndBusinessId(rule.getToOptionId(), businessId).orElse(null);
                if (ruleTarget == null || !component.getId().equals(ruleTarget.getComponentId())) {
                    reasons.add("CONFIGURATION_INCONSISTENT");
                    return;
                }
            }
        }

        // Omission-implies-default (Revision 2, Decision 1): only when a default exists.
        Set<UUID> effectiveSelection = explicitlySelected;
        if (explicitlySelected.isEmpty() && defaultOptionId != null) {
            effectiveSelection = Set.of(defaultOptionId);
        }

        if (component.isRequired() && effectiveSelection.isEmpty()) {
            reasons.add("SLOT_REQUIRED");
            return;
        }
        if (effectiveSelection.size() < component.getMinSelections()) {
            reasons.add("TOO_FEW_SELECTIONS");
        }
        if (component.getMaxSelections() != null && effectiveSelection.size() > component.getMaxSelections()) {
            reasons.add("TOO_MANY_SELECTIONS");
        }

        for (UUID selectedId : effectiveSelection) {
            Option option = optionRepository.findByIdAndBusinessId(selectedId, businessId).orElse(null);
            if (option == null || !component.getId().equals(option.getComponentId())) {
                reasons.add("OPTION_NOT_IN_SLOT");
                continue;
            }
            if (option.isRequiresManualQuote()) {
                manualQuoteRequired.set(true);
            }

            boolean isDefaultOrNoDefault = defaultOptionId == null || selectedId.equals(defaultOptionId);
            if (isDefaultOrNoDefault) {
                // Normal path (Revision 2, Clarification 2): no-default component, or the
                // default itself. Priced via effectivePrice(option) — linked Offering (if any)
                // plus the option's own adjustment.
                BigDecimal contribution = resolveEffectivePrice(businessId, option, reasons);
                if (contribution != null) {
                    adjustments.add(new PricingAdjustment(component.getId(), selectedId, null, contribution, false));
                }
            } else {
                // Non-default selection on a defaulted component — requires a same-component
                // substitution rule (Revision 2, Clarification 2/5).
                SubstitutionRule rule = substitutionRuleRepository
                        .findByBusinessIdAndFromOptionIdAndToOptionId(businessId, defaultOptionId, selectedId)
                        .orElse(null);
                if (rule == null) {
                    reasons.add("SUBSTITUTION_NOT_ALLOWED");
                    continue;
                }
                // No same-component re-check needed here: the proactive scan above (run once,
                // before any selection is processed) already verified every rule rooted at
                // defaultOptionId resolves within this component — a rule reachable by this exact
                // query (keyed by the already-verified defaultOptionId/selectedId pair) is
                // guaranteed consistent by construction once that scan has passed.
                List<UUID> pairKey = List.of(rule.getFromOptionId(), rule.getToOptionId());
                int usageThisCalculation = substitutionUsage.merge(pairKey, 1, Integer::sum);
                if (usageThisCalculation > rule.getMaxCount()) {
                    reasons.add("SUBSTITUTION_LIMIT_EXCEEDED");
                    continue;
                }
                // priceDelta REPLACES the option's own effectivePrice entirely — including any
                // linked-Offering component of it (Revision 2, Clarification 5) — never stacks.
                adjustments.add(new PricingAdjustment(component.getId(), selectedId, null, rule.getPriceDelta(), true));
            }
        }
    }

    /** Normal-path pricing only — never called on the substitution path (Clarification 5). */
    private BigDecimal resolveEffectivePrice(UUID businessId, Option option, List<String> reasons) {
        if (option.getOfferingId() == null) {
            return option.getPriceAdjustment();
        }
        Offering linked = offeringRepository.findByIdAndBusinessId(option.getOfferingId(), businessId).orElse(null);
        if (linked == null) {
            reasons.add("CONFIGURATION_INCONSISTENT");
            return null;
        }
        // Found on re-verification (a real, previously-missing check, not present in the
        // original implementation): Phase 5A's own frozen rule — "only SERVICE Offerings may be
        // referenced by an Option" — was never actually enforced here. Nothing in the database
        // prevents Option.offering_id from pointing at a PACKAGE/PRODUCT/ROOM_TYPE/EVENT_PACKAGE
        // Offering (deliberately, per Phase 5A's own trigger-free posture); this check is what
        // makes that rule real. A mirror of OfferingResolutionService.requireServiceType's own
        // check, inlined here rather than reused, since that method throws for imperative callers
        // and this method instead accumulates a reason code for a pure calculator — the two have
        // incompatible error-handling contracts, so a small amount of duplication is the honest
        // choice over forcing one shape onto the other.
        if (!"SERVICE".equals(linked.getType())) {
            reasons.add("CONFIGURATION_INCONSISTENT");
            return null;
        }
        if (!linked.isActive()) {
            reasons.add("REFERENCED_OFFERING_INACTIVE");
            return null;
        }
        return linked.getBasePrice().add(option.getPriceAdjustment());
    }

    private void processQuantityComponent(UUID businessId, PackageComponent component, Integer submittedQuantity,
                                           List<String> reasons, List<PricingAdjustment> adjustments) {
        QuantityRule rule = quantityRuleRepository.findByBusinessIdAndComponentId(businessId, component.getId()).orElse(null);
        if (rule == null) {
            reasons.add("QUANTITY_RULE_MISSING");
            return;
        }

        int quantity;
        if (submittedQuantity == null) {
            // Structurally unreachable given today's schema: chk_package_components_quantity_kind_bounds
            // (Phase 2, unmodified) forces required=FALSE on every QUANTITY-kind component — kept
            // for the same forward-compatibility reason SUBSTITUTION_LIMIT_EXCEEDED is kept.
            if (component.isRequired()) {
                reasons.add("QUANTITY_REQUIRED");
                return;
            }
            quantity = rule.getMinQuantity(); // omission defaults to the included baseline
        } else {
            quantity = submittedQuantity;
        }

        if (quantity < 0) {
            reasons.add("QUANTITY_NEGATIVE");
            return;
        }
        if (rule.getMaxQuantity() != null && quantity > rule.getMaxQuantity()) {
            reasons.add("QUANTITY_EXCEEDS_MAXIMUM");
            return;
        }

        int overage = Math.max(0, quantity - rule.getMinQuantity());
        BigDecimal contribution = rule.getExtraUnitPrice().multiply(BigDecimal.valueOf(overage));
        adjustments.add(new PricingAdjustment(component.getId(), null, quantity, contribution, false));
    }
}
