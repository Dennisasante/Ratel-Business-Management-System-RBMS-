package com.ratel.rbms.service;

import com.ratel.rbms.entity.*;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5B — proves {@link PackagePricingService} against real
 * PostgreSQL, exactly per the frozen specification (Revision 2). @Transactional rolls every test
 * back; nothing here touches real data permanently.
 *
 * <p>Findings, reported honestly rather than papered over with a misleading test — see the
 * implementation report for full detail:
 * <ul>
 *   <li>Given every source column is NUMERIC(12,2) and the algorithm only ever adds, subtracts,
 *       or multiplies by a plain integer, no reachable input can make final-only rounding differ
 *       from per-step rounding — the distinction the spec asked to be demonstrated does not
 *       materialize under this arithmetic. {@link #finalRoundingIsAppliedExactlyOnceAndIsExact()}
 *       proves the rounding step runs and is exact instead.</li>
 *   <li>{@code SUBSTITUTION_LIMIT_EXCEEDED} and {@code QUANTITY_REQUIRED} remain structurally
 *       unreachable given today's Set-based contract / chk_package_components_quantity_kind_bounds
 *       — not tested as "passing" scenarios.</li>
 *   <li><b>Correction from the prior round</b>: cross-component {@code SubstitutionRule}
 *       corruption is no longer unreachable-by-design — {@link PackagePricingService} now
 *       proactively scans every rule rooted at a component's default (independent of what any
 *       customer actually selects) and rejects the whole component as
 *       {@code CONFIGURATION_INCONSISTENT} if any of them resolves outside that component. See
 *       {@link #configurationInconsistent_crossComponentSubstitutionRuleCorruption()}.</li>
 * </ul>
 */
@SpringBootTest
@Transactional
class Phase5BPackagePricingServiceTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private PackageComponentRepository packageComponentRepository;
    @Autowired private OptionRepository optionRepository;
    @Autowired private SubstitutionRuleRepository substitutionRuleRepository;
    @Autowired private QuantityRuleRepository quantityRuleRepository;
    @Autowired private PackagePricingService pricingService;
    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private ServiceOrderRepository serviceOrderRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private CustomWigRequestRepository customWigRequestRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockMovementRepository stockMovementRepository;

    private Business newBusiness() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return businessRepository.save(Business.builder()
                .name("Phase 5B Test Business " + unique).slug("phase5b-test-business-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
    }

    // ==================== 1. Existing Phase 2 worked examples, through the real calculator ====================

    @Test
    void proteinSlot_defaultChickenIsFree_substitutingToFishCosts20() {
        Business business = newBusiness();
        Offering jollofPackage = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Package 2").basePrice(new BigDecimal("60.00")).build());
        PackageComponent proteinSlot = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(jollofPackage.getId())
                .slotName("Protein").componentKind("SELECTION").required(true).minSelections(1).maxSelections(1).build());
        Option chicken = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(proteinSlot.getId()).label("Chicken").priceAdjustment(BigDecimal.ZERO).build());
        Option fish = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(proteinSlot.getId()).label("Fish").priceAdjustment(new BigDecimal("20.00")).build());
        proteinSlot.setDefaultOptionId(chicken.getId());
        packageComponentRepository.save(proteinSlot);
        substitutionRuleRepository.save(SubstitutionRule.builder()
                .businessId(business.getId()).fromOptionId(chicken.getId()).toOptionId(fish.getId())
                .priceDelta(new BigDecimal("20.00")).maxCount(1).build());

        // Omission implies default (Chicken) — free.
        PricingResult omitted = pricingService.calculate(business.getId(), jollofPackage.getId(), Map.of(), Map.of());
        assertTrue(omitted.valid());
        assertEquals(0, new BigDecimal("60.00").compareTo(omitted.finalPrice()));

        // Explicit default selection — same result.
        PricingResult explicitDefault = pricingService.calculate(business.getId(), jollofPackage.getId(),
                Map.of(proteinSlot.getId(), Set.of(chicken.getId())), Map.of());
        assertEquals(0, new BigDecimal("60.00").compareTo(explicitDefault.finalPrice()));

        // Substitute to Fish — +20 via the SubstitutionRule, not Fish's own priceAdjustment stacked on top.
        PricingResult substituted = pricingService.calculate(business.getId(), jollofPackage.getId(),
                Map.of(proteinSlot.getId(), Set.of(fish.getId())), Map.of());
        assertTrue(substituted.valid(), substituted.reasons().toString());
        assertEquals(0, new BigDecimal("80.00").compareTo(substituted.finalPrice()));
        assertTrue(substituted.adjustments().get(0).viaSubstitution());
    }

    @Test
    void toppings_optionalMultiSelect_noDefaultNoSubstitutionNeeded() {
        Business business = newBusiness();
        Offering pizza = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PRODUCT").name("Margherita Pizza").basePrice(new BigDecimal("45.00")).build());
        PackageComponent toppings = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(pizza.getId())
                .slotName("Extra Toppings").componentKind("SELECTION").required(false).minSelections(0).maxSelections(null).build());
        Option extraCheese = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(toppings.getId()).label("Extra Cheese").priceAdjustment(new BigDecimal("5.00")).build());

        // No default on this component — explicit selection is ordinary, no substitution lookup.
        PricingResult withTopping = pricingService.calculate(business.getId(), pizza.getId(),
                Map.of(toppings.getId(), Set.of(extraCheese.getId())), Map.of());
        assertTrue(withTopping.valid());
        assertEquals(0, new BigDecimal("50.00").compareTo(withTopping.finalPrice()));
        assertFalse(withTopping.adjustments().get(0).viaSubstitution());

        PricingResult withoutTopping = pricingService.calculate(business.getId(), pizza.getId(), Map.of(), Map.of());
        assertTrue(withoutTopping.valid());
        assertEquals(0, new BigDecimal("45.00").compareTo(withoutTopping.finalPrice()));
    }

    @Test
    void poolAccess_optionalSingleToggle() {
        Business business = newBusiness();
        Offering roomPackage = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("ROOM_TYPE").name("Deluxe Room + Breakfast").basePrice(new BigDecimal("400.00")).build());
        PackageComponent poolAccess = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(roomPackage.getId())
                .slotName("Pool Access").componentKind("SELECTION").required(false).minSelections(0).maxSelections(1).build());
        Option addPool = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(poolAccess.getId()).label("Add pool access").priceAdjustment(new BigDecimal("50.00")).build());

        PricingResult withPool = pricingService.calculate(business.getId(), roomPackage.getId(),
                Map.of(poolAccess.getId(), Set.of(addPool.getId())), Map.of());
        assertEquals(0, new BigDecimal("450.00").compareTo(withPool.finalPrice()));
    }

    @Test
    void guestCount_quantityKind_overagePricingMatchesTheWorkedExample() {
        Business business = newBusiness();
        Offering birthdayPackage = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("EVENT_PACKAGE").name("Birthday Package").basePrice(new BigDecimal("2000.00")).build());
        PackageComponent guestCount = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(birthdayPackage.getId())
                .slotName("Guest Count").componentKind("QUANTITY").required(false).minSelections(0).maxSelections(null).build());
        quantityRuleRepository.save(QuantityRule.builder()
                .businessId(business.getId()).componentId(guestCount.getId())
                .minQuantity(20).maxQuantity(null).extraUnitPrice(new BigDecimal("80.00")).build());

        PricingResult with25 = pricingService.calculate(business.getId(), birthdayPackage.getId(), Map.of(),
                Map.of(guestCount.getId(), 25));
        assertTrue(with25.valid());
        assertEquals(0, new BigDecimal("2400.00").compareTo(with25.finalPrice())); // 2000 + (25-20)*80

        PricingResult omitted = pricingService.calculate(business.getId(), birthdayPackage.getId(), Map.of(), Map.of());
        assertTrue(omitted.valid());
        assertEquals(0, new BigDecimal("2000.00").compareTo(omitted.finalPrice())); // defaults to min=20, no overage
    }

    // ==================== 4/5. Linked Offering pricing — the exact GH₵270 example, and substitution overriding it ====================

    @Test
    void linkedOfferingPricing_exact270WorkedExample_neverDoubleCountsTheLinkedBasePrice() {
        Business business = newBusiness();
        Offering spaPackage = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Deluxe Spa Package").basePrice(new BigDecimal("200.00")).build());
        Offering massage = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("SERVICE").name("Swedish Massage").basePrice(new BigDecimal("80.00")).active(true).build());
        PackageComponent addOn = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(spaPackage.getId())
                .slotName("Add-on Massage").componentKind("SELECTION").required(false).minSelections(0).maxSelections(1).build());
        Option addMassage = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(addOn.getId()).offeringId(massage.getId())
                .label("Add Swedish Massage").priceAdjustment(new BigDecimal("-10.00")).build());

        PricingResult withMassage = pricingService.calculate(business.getId(), spaPackage.getId(),
                Map.of(addOn.getId(), Set.of(addMassage.getId())), Map.of());
        assertTrue(withMassage.valid(), withMassage.reasons().toString());
        assertEquals(0, new BigDecimal("270.00").compareTo(withMassage.finalPrice()),
                "200.00 package base + (80.00 linked Offering price + -10.00 adjustment) = 270.00");
        assertNotEquals(0, new BigDecimal("350.00").compareTo(withMassage.finalPrice()),
                "must never be 200 + 80 + 70 — the linked Offering's price must never be added as a second base price");

        PricingResult withoutMassage = pricingService.calculate(business.getId(), spaPackage.getId(), Map.of(), Map.of());
        assertEquals(0, new BigDecimal("200.00").compareTo(withoutMassage.finalPrice()));

        // Live pricing, not cached: the linked Offering's price changing changes a NEW calculation.
        massage.setBasePrice(new BigDecimal("90.00"));
        offeringRepository.save(massage);
        PricingResult afterPriceChange = pricingService.calculate(business.getId(), spaPackage.getId(),
                Map.of(addOn.getId(), Set.of(addMassage.getId())), Map.of());
        assertEquals(0, new BigDecimal("280.00").compareTo(afterPriceChange.finalPrice()));
    }

    @Test
    void substitutionPriceDelta_replacesTheEntireLinkedOfferingContribution_neverStacks() {
        Business business = newBusiness();
        Offering package_ = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Spa Add-on Test Package").basePrice(new BigDecimal("100.00")).build());
        Offering swedishMassageOffering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("SERVICE").name("Swedish Massage").basePrice(new BigDecimal("80.00")).active(true).build());
        PackageComponent massageType = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(package_.getId())
                .slotName("Massage Type").componentKind("SELECTION").required(true).minSelections(1).maxSelections(1).build());
        Option basicMassage = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(massageType.getId()).label("Basic Massage").priceAdjustment(BigDecimal.ZERO).build());
        Option swedishOption = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(massageType.getId()).offeringId(swedishMassageOffering.getId())
                .label("Swedish Massage").priceAdjustment(new BigDecimal("-10.00")).build());
        massageType.setDefaultOptionId(basicMassage.getId());
        packageComponentRepository.save(massageType);
        substitutionRuleRepository.save(SubstitutionRule.builder()
                .businessId(business.getId()).fromOptionId(basicMassage.getId()).toOptionId(swedishOption.getId())
                .priceDelta(new BigDecimal("65.00")).maxCount(1).build());

        PricingResult withDefault = pricingService.calculate(business.getId(), package_.getId(),
                Map.of(massageType.getId(), Set.of(basicMassage.getId())), Map.of());
        assertEquals(0, new BigDecimal("100.00").compareTo(withDefault.finalPrice()));

        PricingResult withSubstitution = pricingService.calculate(business.getId(), package_.getId(),
                Map.of(massageType.getId(), Set.of(swedishOption.getId())), Map.of());
        assertTrue(withSubstitution.valid(), withSubstitution.reasons().toString());
        assertEquals(0, new BigDecimal("165.00").compareTo(withSubstitution.finalPrice()),
                "100.00 base + 65.00 substitution delta = 165.00 — the linked Offering's 80.00 must never be consulted at all on the substitution path");
        assertNotEquals(0, new BigDecimal("170.00").compareTo(withSubstitution.finalPrice()), "not 100+70 (effectivePrice, ignoring the substitution)");
        assertNotEquals(0, new BigDecimal("235.00").compareTo(withSubstitution.finalPrice()), "not 100+65+70 (double counted)");
        assertTrue(withSubstitution.adjustments().get(0).viaSubstitution());
    }

    // ==================== 2/3. Reason codes, default/no-default behaviour ====================

    @Test
    void slotRequired_whenRequiredAndEmptyWithNoDefault() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").basePrice(BigDecimal.TEN).build());
        PackageComponent dessert = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId())
                .slotName("Dessert").componentKind("SELECTION").required(true).minSelections(1).maxSelections(1).build());

        PricingResult result = pricingService.calculate(business.getId(), offering.getId(), Map.of(), Map.of());
        assertFalse(result.valid());
        assertTrue(result.reasons().contains("SLOT_REQUIRED"));
    }

    @Test
    void tooFewAndTooManySelections() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").basePrice(BigDecimal.ZERO).build());
        PackageComponent slot = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId())
                .slotName("Slot").componentKind("SELECTION").required(false).minSelections(2).maxSelections(2).build());
        Option a = optionRepository.save(Option.builder().businessId(business.getId()).componentId(slot.getId()).label("A").build());
        Option b = optionRepository.save(Option.builder().businessId(business.getId()).componentId(slot.getId()).label("B").build());
        Option c = optionRepository.save(Option.builder().businessId(business.getId()).componentId(slot.getId()).label("C").build());

        PricingResult tooFew = pricingService.calculate(business.getId(), offering.getId(), Map.of(slot.getId(), Set.of(a.getId())), Map.of());
        assertTrue(tooFew.reasons().contains("TOO_FEW_SELECTIONS"));

        PricingResult tooMany = pricingService.calculate(business.getId(), offering.getId(),
                Map.of(slot.getId(), Set.of(a.getId(), b.getId(), c.getId())), Map.of());
        assertTrue(tooMany.reasons().contains("TOO_MANY_SELECTIONS"));
    }

    @Test
    void optionNotInSlot() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").basePrice(BigDecimal.ZERO).build());
        PackageComponent slotA = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot A").componentKind("SELECTION").build());
        PackageComponent slotB = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot B").componentKind("SELECTION").build());
        Option optionOfB = optionRepository.save(Option.builder().businessId(business.getId()).componentId(slotB.getId()).label("B's option").build());

        PricingResult result = pricingService.calculate(business.getId(), offering.getId(),
                Map.of(slotA.getId(), Set.of(optionOfB.getId())), Map.of());
        assertTrue(result.reasons().contains("OPTION_NOT_IN_SLOT"));
    }

    @Test
    void substitutionNotAllowed_whenNoMatchingRuleExists() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").basePrice(BigDecimal.ZERO).build());
        PackageComponent slot = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId())
                .slotName("Slot").componentKind("SELECTION").required(true).minSelections(1).maxSelections(1).build());
        Option chicken = optionRepository.save(Option.builder().businessId(business.getId()).componentId(slot.getId()).label("Chicken").build());
        Option beef = optionRepository.save(Option.builder().businessId(business.getId()).componentId(slot.getId()).label("Beef").build());
        slot.setDefaultOptionId(chicken.getId());
        packageComponentRepository.save(slot);
        // No SubstitutionRule chicken->beef exists.

        PricingResult result = pricingService.calculate(business.getId(), offering.getId(), Map.of(slot.getId(), Set.of(beef.getId())), Map.of());
        assertFalse(result.valid());
        assertTrue(result.reasons().contains("SUBSTITUTION_NOT_ALLOWED"));
    }

    @Test
    void referencedOfferingInactive() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").basePrice(BigDecimal.ZERO).build());
        Offering linked = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("SERVICE").name("Retired Service").basePrice(BigDecimal.TEN).active(false).build());
        PackageComponent slot = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot").componentKind("SELECTION")
                .required(false).minSelections(0).maxSelections(1).build());
        Option option = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(slot.getId()).offeringId(linked.getId()).label("Linked").build());

        PricingResult result = pricingService.calculate(business.getId(), offering.getId(), Map.of(slot.getId(), Set.of(option.getId())), Map.of());
        assertTrue(result.reasons().contains("REFERENCED_OFFERING_INACTIVE"));
    }

    @Test
    void componentNotInOffering() {
        Business business = newBusiness();
        Offering offeringA = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("A").basePrice(BigDecimal.ZERO).build());
        Offering offeringB = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("B").basePrice(BigDecimal.ZERO).build());
        PackageComponent componentOfB = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offeringB.getId()).slotName("Slot").componentKind("SELECTION").build());

        PricingResult result = pricingService.calculate(business.getId(), offeringA.getId(), Map.of(componentOfB.getId(), Set.of()), Map.of());
        assertTrue(result.reasons().contains("COMPONENT_NOT_IN_OFFERING"));
    }

    // ---- Quantity reason codes ----
    // QUANTITY_REQUIRED is NOT tested here: chk_package_components_quantity_kind_bounds (Phase 2,
    // unmodified) forces required=FALSE on every QUANTITY-kind component — a required QUANTITY
    // component cannot be constructed via legitimate data at all. The check remains implemented
    // in PackagePricingService for the same forward-compatibility reason SUBSTITUTION_LIMIT_EXCEEDED
    // is kept — see the implementation report.

    @Test
    void quantityNegative_exceedsMaximum_ruleMissing() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("EVENT_PACKAGE").name("Offering").basePrice(BigDecimal.ZERO).build());

        PackageComponent capped = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Capped Qty").componentKind("QUANTITY").required(false).build());
        quantityRuleRepository.save(QuantityRule.builder().businessId(business.getId()).componentId(capped.getId())
                .minQuantity(0).maxQuantity(10).extraUnitPrice(BigDecimal.ONE).build());
        assertTrue(pricingService.calculate(business.getId(), offering.getId(), Map.of(), Map.of(capped.getId(), -1))
                .reasons().contains("QUANTITY_NEGATIVE"));
        assertTrue(pricingService.calculate(business.getId(), offering.getId(), Map.of(), Map.of(capped.getId(), 11))
                .reasons().contains("QUANTITY_EXCEEDS_MAXIMUM"));

        PackageComponent noRule = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("No Rule Qty").componentKind("QUANTITY").required(false).build());
        assertTrue(pricingService.calculate(business.getId(), offering.getId(), Map.of(), Map.of(noRule.getId(), 5))
                .reasons().contains("QUANTITY_RULE_MISSING"));
    }

    // ==================== 7. Manual-quote cascade ====================

    @Test
    void manualQuoteOption_cascadesToWholeResult() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Custom Package").basePrice(new BigDecimal("50.00")).build());
        PackageComponent slot = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Custom Extra").componentKind("SELECTION")
                .required(false).minSelections(0).maxSelections(1).build());
        Option quoteOnly = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(slot.getId()).label("Bespoke Add-on")
                .priceAdjustment(new BigDecimal("15.00")).requiresManualQuote(true).build());

        PricingResult result = pricingService.calculate(business.getId(), offering.getId(), Map.of(slot.getId(), Set.of(quoteOnly.getId())), Map.of());
        assertTrue(result.valid());
        assertTrue(result.manualQuoteRequired());
        assertNull(result.finalPrice(), "finalPrice must be null whenever manual quote is required");
        assertEquals(1, result.adjustments().size(), "the deterministic subtotal contribution is still returned for display/context");
        assertEquals(0, new BigDecimal("15.00").compareTo(result.adjustments().get(0).amount()));
    }

    // ==================== 8. CONFIGURATION_INCONSISTENT (the two genuinely reachable scenarios) ====================

    @Test
    void configurationInconsistent_defaultOptionBelongsToAnotherComponent() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").basePrice(BigDecimal.ZERO).build());
        PackageComponent slotA = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot A").componentKind("SELECTION")
                .required(true).minSelections(1).maxSelections(1).build());
        PackageComponent slotB = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot B").componentKind("SELECTION")
                .required(false).minSelections(0).maxSelections(1).build());
        Option optionOfB = optionRepository.save(Option.builder().businessId(business.getId()).componentId(slotB.getId()).label("B's option").build());
        // Nothing in the DB stops this — the same-row CHECK only requires required=true+max=1,
        // never that the referenced option actually belongs to THIS component.
        slotA.setDefaultOptionId(optionOfB.getId());
        packageComponentRepository.save(slotA);

        PricingResult result = pricingService.calculate(business.getId(), offering.getId(), Map.of(), Map.of());
        assertFalse(result.valid());
        assertTrue(result.reasons().contains("CONFIGURATION_INCONSISTENT"));
    }

    @Test
    void configurationInconsistent_quantityRuleOnASelectionKindComponent() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").basePrice(BigDecimal.ZERO).build());
        PackageComponent selectionComponent = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot").componentKind("SELECTION")
                .required(false).minSelections(0).maxSelections(1).build());
        // Nothing stops a QuantityRule from pointing at a SELECTION-kind component — the FK only
        // requires the component to exist, not to be QUANTITY-kind.
        quantityRuleRepository.save(QuantityRule.builder()
                .businessId(business.getId()).componentId(selectionComponent.getId())
                .minQuantity(0).maxQuantity(null).extraUnitPrice(BigDecimal.TEN).build());

        PricingResult result = pricingService.calculate(business.getId(), offering.getId(), Map.of(), Map.of());
        assertFalse(result.valid());
        assertTrue(result.reasons().contains("CONFIGURATION_INCONSISTENT"));
    }

    // ==================== 9. Tenant isolation ====================

    @Test
    void defaultOptionCrossBusinessReferenceIsRejectedByTheDatabase() {
        Business a = newBusiness();
        Business b = newBusiness();
        Offering offeringB = offeringRepository.save(Offering.builder()
                .businessId(b.getId()).type("PACKAGE").name("B's Offering").basePrice(BigDecimal.ZERO).build());
        PackageComponent componentB = packageComponentRepository.save(PackageComponent.builder()
                .businessId(b.getId()).offeringId(offeringB.getId()).slotName("Slot").componentKind("SELECTION")
                .required(false).minSelections(0).maxSelections(1).build());
        Option optionB = optionRepository.save(Option.builder().businessId(b.getId()).componentId(componentB.getId()).label("B option").build());

        PackageComponent invalid = PackageComponent.builder()
                .businessId(a.getId()) // wrong business for optionB
                .offeringId(offeringRepository.save(Offering.builder().businessId(a.getId()).type("PACKAGE").name("A's Offering").build()).getId())
                .slotName("Cross-business default").componentKind("SELECTION")
                .required(true).minSelections(1).maxSelections(1).defaultOptionId(optionB.getId()).build();

        assertThrows(DataIntegrityViolationException.class,
                () -> packageComponentRepository.saveAndFlush(invalid),
                "fk_package_components_default_option must reject a mismatched (business_id, default_option_id) pair");
    }

    @Test
    void calculateAgainstAnotherBusinesssOfferingIsRejected() {
        Business owner = newBusiness();
        Business intruder = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(owner.getId()).type("PACKAGE").name("Owner's Offering").basePrice(BigDecimal.TEN).build());

        ApiException ex = assertThrows(ApiException.class,
                () -> pricingService.calculate(intruder.getId(), offering.getId(), Map.of(), Map.of()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void selectingAnotherBusinesssOptionResolvesAsOptionNotInSlot_neverLeaksCrossTenantData() {
        Business a = newBusiness();
        Business b = newBusiness();
        Offering offeringA = offeringRepository.save(Offering.builder()
                .businessId(a.getId()).type("PACKAGE").name("A's Offering").basePrice(BigDecimal.ZERO).build());
        PackageComponent slotA = packageComponentRepository.save(PackageComponent.builder()
                .businessId(a.getId()).offeringId(offeringA.getId()).slotName("Slot").componentKind("SELECTION")
                .required(false).minSelections(0).maxSelections(1).build());
        Option optionB = optionRepository.save(Option.builder().businessId(b.getId()).label("B's standalone option").build());

        PricingResult result = pricingService.calculate(a.getId(), offeringA.getId(), Map.of(slotA.getId(), Set.of(optionB.getId())), Map.of());
        assertTrue(result.reasons().contains("OPTION_NOT_IN_SLOT"));
    }

    // ==================== 10. Rounding ====================

    @Test
    void finalRoundingIsAppliedExactlyOnceAndIsExact() {
        // Given every source column is NUMERIC(12,2) and the algorithm only ever adds,
        // subtracts, or multiplies a scale-2 value by a plain integer, no reachable input can
        // make final-only rounding differ from per-step rounding — see this class's own header
        // comment and the implementation report. This test proves the rounding step runs and
        // produces the mathematically exact expected value, not that a distinguishing case exists.
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").basePrice(new BigDecimal("10.01")).build());
        PackageComponent slot = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot").componentKind("SELECTION")
                .required(false).minSelections(0).maxSelections(2).build());
        Option a = optionRepository.save(Option.builder().businessId(business.getId()).componentId(slot.getId()).label("A").priceAdjustment(new BigDecimal("0.33")).build());
        Option c = optionRepository.save(Option.builder().businessId(business.getId()).componentId(slot.getId()).label("C").priceAdjustment(new BigDecimal("0.66")).build());

        PricingResult result = pricingService.calculate(business.getId(), offering.getId(), Map.of(slot.getId(), Set.of(a.getId(), c.getId())), Map.of());
        assertTrue(result.valid());
        assertEquals(2, result.finalPrice().scale());
        assertEquals(0, new BigDecimal("11.00").compareTo(result.finalPrice())); // 10.01 + 0.33 + 0.66 = 11.00 exactly
    }

    // ==================== 11. Purity / no side effects ====================

    @Test
    void calculateIsPure_repeatedCallsIdenticalOutput_noRowsWritten() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").basePrice(new BigDecimal("30.00")).build());
        PackageComponent slot = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot").componentKind("SELECTION")
                .required(false).minSelections(0).maxSelections(1).build());
        Option option = optionRepository.save(Option.builder().businessId(business.getId()).componentId(slot.getId()).label("Add-on").priceAdjustment(new BigDecimal("5.00")).build());

        long offeringCountBefore = offeringRepository.count();
        long componentCountBefore = packageComponentRepository.count();
        long optionCountBefore = optionRepository.count();
        long substitutionCountBefore = substitutionRuleRepository.count();
        long quantityRuleCountBefore = quantityRuleRepository.count();

        PricingResult first = pricingService.calculate(business.getId(), offering.getId(), Map.of(slot.getId(), Set.of(option.getId())), Map.of());
        PricingResult second = pricingService.calculate(business.getId(), offering.getId(), Map.of(slot.getId(), Set.of(option.getId())), Map.of());

        assertEquals(first, second, "identical input against identical catalog state must produce identical output");
        assertEquals(offeringCountBefore, offeringRepository.count());
        assertEquals(componentCountBefore, packageComponentRepository.count());
        assertEquals(optionCountBefore, optionRepository.count());
        assertEquals(substitutionCountBefore, substitutionRuleRepository.count());
        assertEquals(quantityRuleCountBefore, quantityRuleRepository.count());
    }

    @Test
    void calculateWritesNothingAcrossPricingAuditTransactionInventoryOrCatalogTables() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").basePrice(new BigDecimal("20.00")).build());
        PackageComponent slot = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot").componentKind("SELECTION")
                .required(false).minSelections(0).maxSelections(1).build());
        Option option = optionRepository.save(Option.builder().businessId(business.getId()).componentId(slot.getId()).label("Add-on").priceAdjustment(new BigDecimal("5.00")).build());

        long activityLogBefore = activityLogRepository.count();
        long saleBefore = saleRepository.count();
        long serviceOrderBefore = serviceOrderRepository.count();
        long bookingBefore = bookingRepository.count();
        long customWigRequestBefore = customWigRequestRepository.count();
        long productBefore = productRepository.count();
        long stockMovementBefore = stockMovementRepository.count();

        PricingResult result = pricingService.calculate(business.getId(), offering.getId(), Map.of(slot.getId(), Set.of(option.getId())), Map.of());
        assertTrue(result.valid());

        assertEquals(activityLogBefore, activityLogRepository.count(), "no audit rows");
        assertEquals(saleBefore, saleRepository.count(), "no Sale mutations");
        assertEquals(serviceOrderBefore, serviceOrderRepository.count(), "no ServiceOrder mutations");
        assertEquals(bookingBefore, bookingRepository.count(), "no Booking mutations");
        assertEquals(customWigRequestBefore, customWigRequestRepository.count(), "no CustomWigRequest mutations");
        assertEquals(productBefore, productRepository.count(), "no inventory/Product mutations");
        assertEquals(stockMovementBefore, stockMovementRepository.count(), "no stock movement mutations");
    }

    // ==================== 1. Cross-component substitution corruption (proactive detection) ====================

    @Test
    void configurationInconsistent_crossComponentSubstitutionRuleCorruption() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").basePrice(new BigDecimal("100.00")).build());
        PackageComponent componentA = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId())
                .slotName("Component A").componentKind("SELECTION").required(true).minSelections(1).maxSelections(1).build());
        PackageComponent componentB = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId())
                .slotName("Component B").componentKind("SELECTION").required(false).minSelections(0).maxSelections(1).build());
        Option defaultA = optionRepository.save(Option.builder().businessId(business.getId()).componentId(componentA.getId()).label("A Default").build());
        Option optionB = optionRepository.save(Option.builder().businessId(business.getId()).componentId(componentB.getId()).label("B Option").build());
        componentA.setDefaultOptionId(defaultA.getId());
        packageComponentRepository.save(componentA);

        // Nothing in the schema stops this — uq_substitution_rules_pair only guarantees
        // uniqueness of the pair, and the two composite FKs only require each option to belong
        // to the SAME BUSINESS as the rule, never to the same component as each other. A
        // deliberately malformed, cross-component rule, created via a perfectly ordinary save():
        substitutionRuleRepository.save(SubstitutionRule.builder()
                .businessId(business.getId()).fromOptionId(defaultA.getId()).toOptionId(optionB.getId())
                .priceDelta(new BigDecimal("10.00")).maxCount(1).build());

        // The customer never even attempts to use the malformed rule — they simply take the
        // default for component A. The corruption must still be caught, because it makes
        // component A's own substitution catalog unsound regardless of what's actually chosen.
        PricingResult result = pricingService.calculate(business.getId(), offering.getId(), Map.of(), Map.of());
        assertFalse(result.valid());
        assertTrue(result.reasons().contains("CONFIGURATION_INCONSISTENT"),
                "a cross-component substitution rule must be caught proactively, never silently ignored or misreported as SUBSTITUTION_NOT_ALLOWED");
        assertFalse(result.reasons().contains("SUBSTITUTION_NOT_ALLOWED"));
    }

    // ==================== 2. Ambiguous rules — proving duplicates are structurally impossible ====================

    @Test
    void duplicateSubstitutionRulePairIsRejectedByTheDatabase_ambiguityStructurallyImpossible() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").basePrice(BigDecimal.ZERO).build());
        PackageComponent slot = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot").componentKind("SELECTION")
                .required(false).minSelections(0).maxSelections(2).build());
        Option a = optionRepository.save(Option.builder().businessId(business.getId()).componentId(slot.getId()).label("A").build());
        Option b = optionRepository.save(Option.builder().businessId(business.getId()).componentId(slot.getId()).label("B").build());
        substitutionRuleRepository.save(SubstitutionRule.builder()
                .businessId(business.getId()).fromOptionId(a.getId()).toOptionId(b.getId()).priceDelta(BigDecimal.ONE).maxCount(1).build());

        SubstitutionRule duplicate = SubstitutionRule.builder()
                .businessId(business.getId()).fromOptionId(a.getId()).toOptionId(b.getId()).priceDelta(new BigDecimal("999.00")).maxCount(5).build();

        assertThrows(DataIntegrityViolationException.class,
                () -> substitutionRuleRepository.saveAndFlush(duplicate),
                "uq_substitution_rules_pair (Phase 2, unmodified) already makes a second row for the same (from,to) pair impossible — "
                        + "the pricing engine never needs to arbitrarily pick one, because the database never lets a second one exist");
    }

    @Test
    void duplicateQuantityRuleOnOneComponentIsRejectedByTheDatabase_ambiguityStructurallyImpossible() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("EVENT_PACKAGE").name("Offering").basePrice(BigDecimal.ZERO).build());
        PackageComponent component = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Qty").componentKind("QUANTITY").build());
        quantityRuleRepository.save(QuantityRule.builder()
                .businessId(business.getId()).componentId(component.getId()).minQuantity(0).maxQuantity(null).extraUnitPrice(BigDecimal.TEN).build());

        QuantityRule duplicate = QuantityRule.builder()
                .businessId(business.getId()).componentId(component.getId()).minQuantity(5).maxQuantity(50).extraUnitPrice(new BigDecimal("999.00")).build();

        assertThrows(DataIntegrityViolationException.class,
                () -> quantityRuleRepository.saveAndFlush(duplicate),
                "uq_quantity_rules_component (Phase 2, unmodified) already makes a second row on one component impossible — "
                        + "the pricing engine never needs to arbitrarily pick one, because the database never lets a second one exist");
    }

    // ==================== 3. Linked Offering type — SERVICE only ====================

    @Test
    void configurationInconsistent_linkedOfferingIsNotServiceType() {
        Business business = newBusiness();
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").basePrice(new BigDecimal("50.00")).build());
        // Nothing in the schema stops Option.offering_id from pointing at a non-SERVICE Offering
        // — deliberately, per Phase 5A's own trigger-free posture for this exact class of rule.
        Offering linkedPackage = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Another Package").basePrice(new BigDecimal("999.00")).active(true).build());
        PackageComponent slot = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot").componentKind("SELECTION")
                .required(false).minSelections(0).maxSelections(1).build());
        Option optionLinkedToPackage = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(slot.getId()).offeringId(linkedPackage.getId())
                .label("Bad Link").priceAdjustment(BigDecimal.ZERO).build());

        PricingResult result = pricingService.calculate(business.getId(), offering.getId(),
                Map.of(slot.getId(), Set.of(optionLinkedToPackage.getId())), Map.of());
        assertFalse(result.valid());
        assertTrue(result.reasons().contains("CONFIGURATION_INCONSISTENT"),
                "an Option referencing a non-SERVICE Offering must be rejected deterministically, never silently priced using that Offering's basePrice");
    }
}
