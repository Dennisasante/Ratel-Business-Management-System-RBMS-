package com.ratel.rbms.service;

import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.ModuleDefinition;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.ModuleDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 1 — proves the new module_definitions
 * reference table is correct AND that adding it changed nothing about how
 * an existing business's real entitlements are computed. @Transactional
 * rolls every test back — nothing here touches real data permanently.
 *
 * The four fixture combinations in entitlementIsUnchangedForRealProductionShapes()
 * are the exact enabled_modules values found on production's four real
 * businesses (read-only query, this phase's own pre-implementation
 * inspection) — not synthetic examples.
 */
@SpringBootTest
@Transactional
class ModuleDefinitionPhase1Test {

    @Autowired
    private ModuleDefinitionRepository moduleDefinitionRepository;

    @Autowired
    private ModuleAccessService moduleAccessService;

    @Autowired
    private BusinessRepository businessRepository;

    // ---- 4: canonical module identifiers resolve correctly ----

    // The definitive canonical set, reconciled directly from source after a
    // real counting error in an earlier report (that report said Serenity's
    // ModuleCode had 12 constants; a fresh, programmatic recount found the
    // true number is 14 — confirmed via `grep -oE 'public static final
    // String [A-Z_]+' ModuleCode.java | wc -l`). RBMS_MODULES (10, from
    // PlatformBusinessService.CORE_MODULES + TOGGLEABLE_MODULES) union
    // SERENITY_MODULE_CODES (14, from ModuleCode.java) union {EVENTS} (new
    // to both) = 23, with exactly 2 real overlaps (AI, INVENTORY). Written
    // as an explicit literal set — not derived by arithmetic in this test —
    // so any future drift is a deliberate, reviewed change to this
    // assertion, never an accident that merely happens to keep some count
    // matching.
    private static final Set<String> RBMS_MODULES = Set.of(
            "INVENTORY", "SALES", "CUSTOMERS", "EXPENSES",
            "SERVICE_ORDERS", "CUSTOM_WIG_REQUESTS", "ECOMMERCE", "BOOKINGS", "SUPPLIERS_AND_PURCHASING", "AI"
    );

    private static final Set<String> SERENITY_ONLY_MODULES = Set.of(
            "POS", "RESERVATIONS", "ACCOMMODATION", "WHATSAPP", "INSTAGRAM", "FACEBOOK",
            "VOICE", "WEBCHAT", "CRM", "TICKETS", "CAMPAIGNS", "INTEGRATIONS"
    );

    private static final Set<String> EXPECTED_CANONICAL_CODES;
    static {
        Set<String> all = new java.util.HashSet<>(RBMS_MODULES);
        all.addAll(SERENITY_ONLY_MODULES);
        all.add("EVENTS");
        EXPECTED_CANONICAL_CODES = Set.copyOf(all);
    }

    @Test
    void databaseContainsExactlyTheDefinitiveCanonicalSetNoMoreNoFewer() {
        Set<String> actual = moduleDefinitionRepository.findAll().stream()
                .map(ModuleDefinition::getCode)
                .collect(Collectors.toSet());

        assertEquals(EXPECTED_CANONICAL_CODES, actual,
                "the seeded table must equal the definitive canonical set exactly — this replaces a prior "
                        + "size()==23 assertion, which would have passed even with the wrong 23 codes seeded");
        assertEquals(23, EXPECTED_CANONICAL_CODES.size(), "sanity check on the literal expected set itself");
    }

    @Test
    void noDuplicateCanonicalCodesExist() {
        List<ModuleDefinition> all = moduleDefinitionRepository.findAll();
        Set<String> distinctCodes = all.stream().map(ModuleDefinition::getCode).collect(Collectors.toSet());
        assertEquals(all.size(), distinctCodes.size(), "every row must have a unique code");
    }

    @Test
    void everyRbmsEnforcedModuleMapsToExactlyOneCanonicalCode() {
        for (String code : RBMS_MODULES) {
            List<ModuleDefinition> matches = moduleDefinitionRepository.findAll().stream()
                    .filter(d -> d.getCode().equals(code))
                    .toList();
            assertEquals(1, matches.size(), "RBMS module " + code + " must map to exactly one canonical row");
        }
    }

    @Test
    void everyIntentionallyRetainedSerenityOnlyModuleIsRepresented() {
        for (String code : SERENITY_ONLY_MODULES) {
            assertTrue(moduleDefinitionRepository.findById(code).isPresent(),
                    "Serenity-only module " + code + " must be represented in the canonical table");
        }
    }

    @Test
    void bookingsAndReservationsRemainSemanticallyDistinctNotMerged() {
        // RBMS's BOOKINGS (live BookingService — service-catalog appointment
        // booking) and Serenity's RESERVATIONS (unbuilt — table/room
        // reservation) are adjacent, never merged into one code, per explicit
        // instruction. Both must exist as separate rows, and BOOKINGS'
        // metadata must not reference RESERVATIONS or vice versa.
        ModuleDefinition bookings = moduleDefinitionRepository.findById("BOOKINGS").orElseThrow();
        ModuleDefinition reservations = moduleDefinitionRepository.findById("RESERVATIONS").orElseThrow();

        assertNotEquals(bookings.getCode(), reservations.getCode());
        assertFalse(bookings.getRequiredModules().contains("RESERVATIONS"));
        assertFalse(reservations.getRequiredModules().contains("BOOKINGS"));
        assertEquals("RBMS", bookings.getSourceSystem());
        assertEquals("SERENITY", reservations.getSourceSystem());
        assertTrue(bookings.isAlreadyEnforced(), "BOOKINGS is real, live RBMS code today");
        assertFalse(reservations.isAlreadyEnforced(), "RESERVATIONS has no enforcement anywhere yet");
    }

    @Test
    void noUnknownCanonicalCodeIsAccepted() {
        assertTrue(moduleDefinitionRepository.findById("RANDOM_UNRECOGNIZED_CODE").isEmpty());
        assertTrue(moduleDefinitionRepository.findById("RESTAURANT").isEmpty(),
                "RESTAURANT is an industry_group value, not itself a canonical module code — must not silently exist as one");
        assertTrue(moduleDefinitionRepository.findById("HOSPITALITY").isEmpty(),
                "same reasoning — HOSPITALITY is an industry_group value, not a module code");
    }

    @Test
    void everyCodeCurrentlyEnforcedInProductionIsMarkedAlreadyEnforced() {
        // The exact 10 codes ModuleAccessService/PlatformBusinessService gate
        // or validate today — see PlatformBusinessService.CORE_MODULES/TOGGLEABLE_MODULES.
        Set<String> expectedEnforced = Set.of(
                "INVENTORY", "SALES", "CUSTOMERS", "EXPENSES",
                "SERVICE_ORDERS", "CUSTOM_WIG_REQUESTS", "ECOMMERCE", "BOOKINGS", "SUPPLIERS_AND_PURCHASING", "AI"
        );

        List<ModuleDefinition> enforced = moduleDefinitionRepository.findAll().stream()
                .filter(ModuleDefinition::isAlreadyEnforced)
                .toList();

        Set<String> actualEnforced = enforced.stream().map(ModuleDefinition::getCode).collect(Collectors.toSet());
        assertEquals(expectedEnforced, actualEnforced,
                "already_enforced must match production's real gate exactly — this is what makes 'legacy identifiers remain compatible' verifiable, not assumed");
    }

    @Test
    void serenityAndProposedCodesAreReservedNotEnforced() {
        List<String> notYetEnforced = List.of(
                "POS", "RESERVATIONS", "ACCOMMODATION", "WHATSAPP", "INSTAGRAM", "FACEBOOK",
                "VOICE", "WEBCHAT", "CRM", "TICKETS", "CAMPAIGNS", "INTEGRATIONS", "EVENTS"
        );
        for (String code : notYetEnforced) {
            ModuleDefinition def = moduleDefinitionRepository.findById(code)
                    .orElseThrow(() -> new AssertionError(code + " must exist in the seeded vocabulary"));
            assertFalse(def.isAlreadyEnforced(), code + " must not be marked enforced — nothing gates it yet");
        }
    }

    // ---- 5: required-module relationships work correctly ----

    @Test
    void requiredModuleRelationshipsAreStoredCorrectly() {
        assertEquals(List.of("SERVICE_ORDERS"), moduleDefinitionRepository.findById("CUSTOM_WIG_REQUESTS").orElseThrow().getRequiredModules());
        assertEquals(List.of("INVENTORY"), moduleDefinitionRepository.findById("POS").orElseThrow().getRequiredModules());
        assertEquals(List.of("AI"), moduleDefinitionRepository.findById("WHATSAPP").orElseThrow().getRequiredModules());
        assertTrue(moduleDefinitionRepository.findById("INVENTORY").orElseThrow().getRequiredModules().isEmpty(),
                "a CORE module must not require anything else");
    }

    // ---- 6: default-for-profile relationships work correctly ----

    @Test
    void defaultForProfileRelationshipsAreStoredCorrectly() {
        assertTrue(moduleDefinitionRepository.findById("INVENTORY").orElseThrow().getDefaultForProfiles()
                .containsAll(List.of("RETAIL", "RESTAURANT", "HOSPITALITY", "SERVICES", "MULTI")));
        assertEquals(List.of("RESTAURANT"), moduleDefinitionRepository.findById("POS").orElseThrow().getDefaultForProfiles());
        assertEquals(List.of("HOSPITALITY"), moduleDefinitionRepository.findById("ACCOMMODATION").orElseThrow().getDefaultForProfiles());
    }

    // ---- 7: unknown/invalid module identifiers fail safely ----

    @Test
    void unknownModuleCodeIsAbsentFromTheDefinitionTable() {
        Optional<ModuleDefinition> unknown = moduleDefinitionRepository.findById("NOT_A_REAL_MODULE");
        assertTrue(unknown.isEmpty());
    }

    @Test
    void unknownModuleCodeStillFailsSafelyThroughTheExistingGate() {
        // Proves module_definitions' mere existence hasn't changed
        // ModuleAccessService's own behavior for a code it doesn't
        // recognize — it must still just return false, not throw or 500.
        Business business = businessRepository.save(newBusiness());
        assertFalse(moduleAccessService.hasModule(business.getId(), "NOT_A_REAL_MODULE"));
    }

    // ---- 1 & 2: existing entitlement and access rules are unchanged, proven
    // against the real, current production shapes (captured read-only,
    // this phase's own pre-implementation inspection) ----

    @Test
    void entitlementIsUnchangedForRealProductionShapes() {
        // Business A — full toggleable set including AI (matches "Dennis Test Account").
        assertEntitlement(
                List.of("INVENTORY", "EXPENSES", "CUSTOMERS", "SALES", "SERVICE_ORDERS",
                        "CUSTOM_WIG_REQUESTS", "ECOMMERCE", "BOOKINGS", "SUPPLIERS_AND_PURCHASING", "AI"),
                Set.of("SERVICE_ORDERS", "CUSTOM_WIG_REQUESTS", "ECOMMERCE", "BOOKINGS", "SUPPLIERS_AND_PURCHASING", "AI"),
                Set.of()
        );

        // Business B — default backfilled set, AI never granted (matches "Chelle Luxury Hair").
        assertEntitlement(
                List.of("INVENTORY", "SALES", "CUSTOMERS", "EXPENSES", "SERVICE_ORDERS",
                        "CUSTOM_WIG_REQUESTS", "ECOMMERCE", "BOOKINGS", "SUPPLIERS_AND_PURCHASING"),
                Set.of("SERVICE_ORDERS", "CUSTOM_WIG_REQUESTS", "ECOMMERCE", "BOOKINGS", "SUPPLIERS_AND_PURCHASING"),
                Set.of("AI")
        );

        // Business C — minimal, Super-Admin-restricted set (matches "D.K.K.HUB DEALS").
        assertEntitlement(
                List.of("EXPENSES", "INVENTORY", "SALES", "CUSTOMERS", "SUPPLIERS_AND_PURCHASING"),
                Set.of("SUPPLIERS_AND_PURCHASING"),
                Set.of("SERVICE_ORDERS", "CUSTOM_WIG_REQUESTS", "ECOMMERCE", "BOOKINGS", "AI")
        );

        // Business D — AI granted, wig requests not (matches "Paradise Beach Resort (Demo)").
        assertEntitlement(
                List.of("EXPENSES", "INVENTORY", "SALES", "CUSTOMERS", "SERVICE_ORDERS",
                        "ECOMMERCE", "BOOKINGS", "SUPPLIERS_AND_PURCHASING", "AI"),
                Set.of("SERVICE_ORDERS", "ECOMMERCE", "BOOKINGS", "SUPPLIERS_AND_PURCHASING", "AI"),
                Set.of("CUSTOM_WIG_REQUESTS")
        );
    }

    private void assertEntitlement(List<String> enabledModules, Set<String> expectedGranted, Set<String> expectedDenied) {
        Business business = newBusiness();
        business.setEnabledModules(enabledModules);
        business = businessRepository.save(business);

        for (String code : expectedGranted) {
            assertTrue(moduleAccessService.hasModule(business.getId(), code),
                    "expected " + code + " to be granted for enabledModules=" + enabledModules);
        }
        for (String code : expectedDenied) {
            assertFalse(moduleAccessService.hasModule(business.getId(), code),
                    "expected " + code + " to be denied for enabledModules=" + enabledModules);
        }
    }

    private Business newBusiness() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return Business.builder()
                .name("Phase 1 Test Business " + unique)
                .slug("phase1-test-business-" + unique)
                .industry(Industry.OTHER)
                .currency("GHS")
                .build();
    }
}
