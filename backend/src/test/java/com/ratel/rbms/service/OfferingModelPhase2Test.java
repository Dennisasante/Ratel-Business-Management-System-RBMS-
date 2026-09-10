package com.ratel.rbms.service;

import com.ratel.rbms.entity.*;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 2 — proves the Offering/PackageComponent/
 * Option/SubstitutionRule/QuantityRule model (architecture proposal §F)
 * represents the four worked examples, and that the database itself —
 * not application code — refuses every cross-business reference and
 * every invariant violation this phase's own review settled on.
 * @Transactional rolls every test back; nothing here touches real data
 * permanently. Each constraint-violation test is the last action in its
 * own method — Postgres aborts the rest of a transaction after a failed
 * statement, so nothing meaningful can follow one in the same test.
 */
@SpringBootTest
@Transactional
class OfferingModelPhase2Test {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private OfferingRepository offeringRepository;
    @Autowired private PackageComponentRepository packageComponentRepository;
    @Autowired private OptionRepository optionRepository;
    @Autowired private SubstitutionRuleRepository substitutionRuleRepository;
    @Autowired private QuantityRuleRepository quantityRuleRepository;

    // ---- Worked example 1: package protein selection (mandatory, exactly one) ----

    @Test
    void proteinSlotIsAMandatorySingleSelectionWithASubstitutionRule() {
        Business business = businessRepository.save(newBusiness());

        Offering jollofPackage = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Package 2")
                .basePrice(new BigDecimal("60.00")).build());

        PackageComponent proteinSlot = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(jollofPackage.getId())
                .slotName("Protein").componentKind("SELECTION")
                .required(true).minSelections(1).maxSelections(1).build());

        Option chicken = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(proteinSlot.getId())
                .label("Chicken").priceAdjustment(BigDecimal.ZERO).build());
        Option fish = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(proteinSlot.getId())
                .label("Fish").priceAdjustment(new BigDecimal("20.00")).build());

        SubstitutionRule chickenToFish = substitutionRuleRepository.save(SubstitutionRule.builder()
                .businessId(business.getId()).fromOptionId(chicken.getId()).toOptionId(fish.getId())
                .priceDelta(new BigDecimal("20.00")).maxCount(1).build());

        assertTrue(proteinSlot.isRequired());
        assertEquals(1, proteinSlot.getMinSelections());
        assertEquals(1, proteinSlot.getMaxSelections());
        assertEquals(new BigDecimal("20.00"), chickenToFish.getPriceDelta());
    }

    // ---- Worked example 2: pizza topping modifier group (optional, many) ----

    @Test
    void toppingsAreAnOptionalMultiSelectModifierGroup() {
        Business business = businessRepository.save(newBusiness());

        Offering pizza = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PRODUCT").name("Margherita Pizza")
                .basePrice(new BigDecimal("45.00")).build());

        PackageComponent toppings = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(pizza.getId())
                .slotName("Extra Toppings").componentKind("SELECTION")
                .required(false).minSelections(0).maxSelections(null).build());

        optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(toppings.getId())
                .label("Extra Cheese").priceAdjustment(new BigDecimal("5.00")).build());

        assertFalse(toppings.isRequired());
        assertEquals(0, toppings.getMinSelections());
        assertNull(toppings.getMaxSelections());
    }

    // ---- Worked example 3: optional pool-access toggle (optional, exactly one) ----

    @Test
    void poolAccessIsAnOptionalSingleToggle() {
        Business business = businessRepository.save(newBusiness());

        Offering roomPackage = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("ROOM_TYPE").name("Deluxe Room + Breakfast")
                .basePrice(new BigDecimal("400.00")).build());

        PackageComponent poolAccess = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(roomPackage.getId())
                .slotName("Pool Access").componentKind("SELECTION")
                .required(false).minSelections(0).maxSelections(1).build());

        optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(poolAccess.getId())
                .label("Add pool access").priceAdjustment(new BigDecimal("50.00")).build());

        assertFalse(poolAccess.isRequired());
        assertEquals(0, poolAccess.getMinSelections());
        assertEquals(1, poolAccess.getMaxSelections());
    }

    // ---- Worked example 4: birthday guest count (QUANTITY-kind, no discrete options) ----

    @Test
    void guestCountIsAQuantityKindComponentGovernedByItsQuantityRule() {
        Business business = businessRepository.save(newBusiness());

        Offering birthdayPackage = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("EVENT_PACKAGE").name("Birthday Package")
                .basePrice(new BigDecimal("2000.00")).build());

        PackageComponent guestCount = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(birthdayPackage.getId())
                .slotName("Guest Count").componentKind("QUANTITY")
                .required(false).minSelections(0).maxSelections(null).build());

        QuantityRule overage = quantityRuleRepository.save(QuantityRule.builder()
                .businessId(business.getId()).componentId(guestCount.getId())
                .minQuantity(20).maxQuantity(null).extraUnitPrice(new BigDecimal("80.00")).build());

        assertEquals("QUANTITY", guestCount.getComponentKind());
        assertEquals(20, overage.getMinQuantity());
        assertEquals(new BigDecimal("80.00"), overage.getExtraUnitPrice());
    }

    @Test
    void quantityKindComponentCannotCarrySelectionKindBounds() {
        Business business = businessRepository.save(newBusiness());
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("EVENT_PACKAGE").name("Bad Quantity Component")
                .build());

        PackageComponent invalid = PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId())
                .slotName("Guest Count").componentKind("QUANTITY")
                .required(true).minSelections(1).maxSelections(5) // forbidden for QUANTITY kind
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> packageComponentRepository.saveAndFlush(invalid),
                "chk_package_components_quantity_kind_bounds must reject this");
    }

    // ---- Composite-FK tenant isolation: a cross-business reference must be a DB-level violation ----

    @Test
    void componentCannotBeInsertedUnderAnotherBusinesssOffering() {
        Business businessA = businessRepository.save(newBusiness());
        Business businessB = businessRepository.save(newBusiness());

        Offering offeringA = offeringRepository.save(Offering.builder()
                .businessId(businessA.getId()).type("PRODUCT").name("A's Offering").build());

        PackageComponent crossBusiness = PackageComponent.builder()
                .businessId(businessB.getId()) // wrong business for offeringA
                .offeringId(offeringA.getId())
                .slotName("Slot").build();

        assertThrows(DataIntegrityViolationException.class,
                () -> packageComponentRepository.saveAndFlush(crossBusiness),
                "fk_package_components_offering must reject a mismatched (business_id, offering_id) pair");
    }

    @Test
    void optionCannotReferenceAnotherBusinesssComponent() {
        Business businessA = businessRepository.save(newBusiness());
        Business businessB = businessRepository.save(newBusiness());

        Offering offeringA = offeringRepository.save(Offering.builder()
                .businessId(businessA.getId()).type("PRODUCT").name("A's Offering").build());
        PackageComponent componentA = packageComponentRepository.save(PackageComponent.builder()
                .businessId(businessA.getId()).offeringId(offeringA.getId()).slotName("Slot").build());

        Option crossBusiness = Option.builder()
                .businessId(businessB.getId()) // wrong business for componentA
                .componentId(componentA.getId())
                .label("Bad Option").build();

        assertThrows(DataIntegrityViolationException.class,
                () -> optionRepository.saveAndFlush(crossBusiness),
                "fk_options_component must reject a mismatched (business_id, component_id) pair");
    }

    @Test
    void substitutionCannotConnectOptionsFromDifferentBusinesses() {
        Business businessA = businessRepository.save(newBusiness());
        Business businessB = businessRepository.save(newBusiness());

        Offering offeringA = offeringRepository.save(Offering.builder()
                .businessId(businessA.getId()).type("PRODUCT").name("A's Offering").build());
        PackageComponent componentA = packageComponentRepository.save(PackageComponent.builder()
                .businessId(businessA.getId()).offeringId(offeringA.getId()).slotName("Slot").build());
        Option optionA = optionRepository.save(Option.builder()
                .businessId(businessA.getId()).componentId(componentA.getId()).label("A Option").build());

        Option standaloneOptionB = optionRepository.save(Option.builder()
                .businessId(businessB.getId()).label("B Standalone Option").build());

        // Attempt a substitution rule under business A that reaches into business B's option.
        SubstitutionRule crossBusiness = SubstitutionRule.builder()
                .businessId(businessA.getId())
                .fromOptionId(optionA.getId())
                .toOptionId(standaloneOptionB.getId()) // wrong business
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> substitutionRuleRepository.saveAndFlush(crossBusiness),
                "fk_substitution_rules_to_option must reject a mismatched (business_id, to_option_id) pair");
    }

    @Test
    void quantityRuleCannotReferenceAnotherBusinesssComponent() {
        Business businessA = businessRepository.save(newBusiness());
        Business businessB = businessRepository.save(newBusiness());

        Offering offeringA = offeringRepository.save(Offering.builder()
                .businessId(businessA.getId()).type("EVENT_PACKAGE").name("A's Offering").build());
        PackageComponent componentA = packageComponentRepository.save(PackageComponent.builder()
                .businessId(businessA.getId()).offeringId(offeringA.getId())
                .slotName("Slot").componentKind("QUANTITY").build());

        QuantityRule crossBusiness = QuantityRule.builder()
                .businessId(businessB.getId()) // wrong business for componentA
                .componentId(componentA.getId())
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> quantityRuleRepository.saveAndFlush(crossBusiness),
                "fk_quantity_rules_component must reject a mismatched (business_id, component_id) pair");
    }

    @Test
    void standaloneOptionOwnershipIsUnambiguousEvenWithoutAComponent() {
        Business business = businessRepository.save(newBusiness());

        Option standalone = optionRepository.save(Option.builder()
                .businessId(business.getId())
                .label("Standalone Add-on")
                .priceAdjustment(new BigDecimal("10.00"))
                .build());

        assertNull(standalone.getComponentId());
        assertEquals(business.getId(), standalone.getBusinessId());
    }

    @Test
    void standaloneOptionUnderAnUnknownBusinessIsRejected() {
        Option orphan = Option.builder()
                .businessId(UUID.randomUUID()) // no such business
                .label("Orphan Option")
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> optionRepository.saveAndFlush(orphan),
                "options.business_id's own direct FK to businesses must reject an unknown business");
    }

    // ---- Other CHECK/UNIQUE constraints settled across this phase's own review ----

    @Test
    void selfSubstitutionIsProhibited() {
        Business business = businessRepository.save(newBusiness());
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").build());
        PackageComponent component = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot").build());
        Option option = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(component.getId()).label("Only Option").build());

        SubstitutionRule selfSub = SubstitutionRule.builder()
                .businessId(business.getId()).fromOptionId(option.getId()).toOptionId(option.getId())
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> substitutionRuleRepository.saveAndFlush(selfSub),
                "chk_substitution_rules_not_self must reject from_option_id == to_option_id");
    }

    @Test
    void maxCountBelowOneIsRejected() {
        Business business = businessRepository.save(newBusiness());
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").build());
        PackageComponent component = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot")
                .minSelections(0).maxSelections(2).build());
        Option a = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(component.getId()).label("A").build());
        Option b = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(component.getId()).label("B").build());

        SubstitutionRule zeroMaxCount = SubstitutionRule.builder()
                .businessId(business.getId()).fromOptionId(a.getId()).toOptionId(b.getId())
                .maxCount(0).build();

        assertThrows(DataIntegrityViolationException.class,
                () -> substitutionRuleRepository.saveAndFlush(zeroMaxCount),
                "chk_substitution_rules_max_count_positive must reject max_count < 1");
    }

    @Test
    void maxSelectionsBelowMinSelectionsIsRejected() {
        Business business = businessRepository.save(newBusiness());
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").build());

        PackageComponent invalid = PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot")
                .minSelections(3).maxSelections(1).build();

        assertThrows(DataIntegrityViolationException.class,
                () -> packageComponentRepository.saveAndFlush(invalid),
                "chk_package_components_max_ge_min must reject max < min");
    }

    @Test
    void duplicateSlotNameWithinOneOfferingIsRejected() {
        Business business = businessRepository.save(newBusiness());
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").build());
        packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Protein").build());

        PackageComponent duplicate = PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Protein").build();

        assertThrows(DataIntegrityViolationException.class,
                () -> packageComponentRepository.saveAndFlush(duplicate),
                "uq_package_components_offering_slot must reject a duplicate slot_name on one offering");
    }

    @Test
    void duplicateSubstitutionPairIsRejected() {
        Business business = businessRepository.save(newBusiness());
        Offering offering = offeringRepository.save(Offering.builder()
                .businessId(business.getId()).type("PACKAGE").name("Offering").build());
        PackageComponent component = packageComponentRepository.save(PackageComponent.builder()
                .businessId(business.getId()).offeringId(offering.getId()).slotName("Slot")
                .minSelections(0).maxSelections(2).build());
        Option a = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(component.getId()).label("A").build());
        Option b = optionRepository.save(Option.builder()
                .businessId(business.getId()).componentId(component.getId()).label("B").build());
        substitutionRuleRepository.save(SubstitutionRule.builder()
                .businessId(business.getId()).fromOptionId(a.getId()).toOptionId(b.getId()).build());

        SubstitutionRule duplicatePair = SubstitutionRule.builder()
                .businessId(business.getId()).fromOptionId(a.getId()).toOptionId(b.getId()).build();

        assertThrows(DataIntegrityViolationException.class,
                () -> substitutionRuleRepository.saveAndFlush(duplicatePair),
                "uq_substitution_rules_pair must reject a duplicate (from_option_id, to_option_id)");
    }

    @Test
    void invalidOfferingTypeIsRejected() {
        Business business = businessRepository.save(newBusiness());
        Offering invalid = Offering.builder()
                .businessId(business.getId()).type("NOT_A_REAL_TYPE").name("Bad Offering").build();

        assertThrows(DataIntegrityViolationException.class,
                () -> offeringRepository.saveAndFlush(invalid),
                "chk_offerings_type must reject a type outside the fixed vocabulary");
    }

    private Business newBusiness() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return Business.builder()
                .name("Phase 2 Test Business " + unique)
                .slug("phase2-test-business-" + unique)
                .industry(Industry.OTHER)
                .currency("GHS")
                .build();
    }
}
