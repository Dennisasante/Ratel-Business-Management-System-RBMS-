package com.ratel.rbms.service;

import com.ratel.rbms.dto.BookableServiceResponse;
import com.ratel.rbms.dto.DemoSeedResponse;
import com.ratel.rbms.dto.PackageOptionsResponse;
import com.ratel.rbms.dto.PackagePricingPreviewResponse;
import com.ratel.rbms.entity.AiKnowledgeEntry;
import com.ratel.rbms.entity.enums.BookingCutoverState;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.AiKnowledgeEntryRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Restaurant-AI-demo phase — proves the Cafe Bar Noir demo tenant itself is seeded correctly:
 * idempotent, canonically cutover (so the new AI package tools actually work against it),
 * structurally correct packages/substitutions/pricing, and a real, gate-enforcing policy.
 * Deliberately does NOT re-verify PackagePricingService's own arithmetic (Phase 5B's own test
 * suite already does that exhaustively) — only that THIS seed data feeds it correctly.
 */
@SpringBootTest
@Transactional
class CafeBarNoirDemoSeedServiceTest {

    @Autowired
    private CafeBarNoirDemoSeedService seedService;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ServicePackageRepository servicePackageRepository;
    @Autowired
    private AiKnowledgeEntryRepository aiKnowledgeEntryRepository;
    @Autowired
    private BookingCutoverStateResolver bookingCutoverStateResolver;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private PolicyEngine policyEngine;

    @Test
    void seedingIsDisabledUnlessTheFlagIsPassedTrue() {
        assertThrows(ApiException.class, () -> seedService.seedCafeBarNoir(false));
    }

    @Test
    void seedingIsIdempotentByBusinessSlug() {
        // Deliberately does NOT assert first.created() one way or the other: against a genuinely
        // shared dev database (as opposed to a fresh/CI one), a real Cafe Bar Noir tenant may
        // already exist from an actual demo run — that's exactly the idempotent-by-slug property
        // this test exists to prove, not a case to special-case away. What must always hold,
        // regardless of that: a SECOND call in the same test never creates a second one, and both
        // calls agree on the same businessId.
        DemoSeedResponse first = seedService.seedCafeBarNoir(true);

        DemoSeedResponse second = seedService.seedCafeBarNoir(true);
        assertFalse(second.created());
        assertEquals(first.businessId(), second.businessId());

        assertEquals(1, businessRepository.findBySlug("cafe-bar-noir-demo").stream().count());
    }

    @Test
    void seededBusinessIsCanonicallyEnabledSoPackageToolsWork() {
        DemoSeedResponse seed = seedService.seedCafeBarNoir(true);
        assertEquals(BookingCutoverState.CANONICAL_ENABLED, bookingCutoverStateResolver.resolve(seed.businessId()));
        assertTrue(bookingCutoverStateResolver.useCanonical(seed.businessId()));
    }

    @Test
    void fourBookablePackagesAreListedWithRealPrices() {
        DemoSeedResponse seed = seedService.seedCafeBarNoir(true);
        assertEquals(4, servicePackageRepository.findAllByBusinessIdAndActiveTrueAndBookableOnlineTrueOrderByNameAsc(seed.businessId()).size());

        List<BookableServiceResponse> listed = bookingService.listBookableServices(seed.businessId());
        List<BookableServiceResponse> packages = listed.stream().filter(BookableServiceResponse::isPackage).toList();
        assertEquals(4, packages.size());

        BookableServiceResponse classic = packages.stream().filter(p -> "Classic Dinner".equals(p.serviceName())).findFirst()
                .orElseThrow(() -> new AssertionError("Classic Dinner package not found"));
        // 150 (Grilled Pepper Chicken) + 75 (Fried Rice) + 140 (Green Salad) + 75 (Apple Tart) = 440.00
        assertEquals(0, new BigDecimal("440.00").compareTo(classic.price()));
    }

    @Test
    void packageOptionsExposeRealSubstitutionsAndDeltas() {
        DemoSeedResponse seed = seedService.seedCafeBarNoir(true);
        UUID classicId = servicePackageRepository.findAllByBusinessIdOrderByNameAsc(seed.businessId()).stream()
                .filter(p -> "Classic Dinner".equals(p.getName())).findFirst().orElseThrow().getId();

        PackageOptionsResponse options = bookingService.getPackageOptions(seed.businessId(), classicId);
        assertEquals(4, options.components().size());

        PackageOptionsResponse.PackageComponentOptions main = options.components().stream()
                .filter(c -> "Main".equals(c.slotName())).findFirst().orElseThrow();
        assertEquals("Grilled Pepper Chicken", main.defaultLabel());
        // Snapper (150) - Grilled Pepper Chicken (150) = 0.00 delta
        var snapper = main.alternatives().stream().filter(a -> "Snapper".equals(a.label())).findFirst().orElseThrow();
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(snapper.priceDelta()));
        // Tilapia (170) - Grilled Pepper Chicken (150) = +20.00 delta
        var tilapia = main.alternatives().stream().filter(a -> "Tilapia".equals(a.label())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("20.00").compareTo(tilapia.priceDelta()));
    }

    @Test
    void previewPricingAppliesSubstitutionAndPartySizeAuthoritatively() {
        DemoSeedResponse seed = seedService.seedCafeBarNoir(true);
        UUID classicId = servicePackageRepository.findAllByBusinessIdOrderByNameAsc(seed.businessId()).stream()
                .filter(p -> "Classic Dinner".equals(p.getName())).findFirst().orElseThrow().getId();
        PackageOptionsResponse options = bookingService.getPackageOptions(seed.businessId(), classicId);
        UUID mainComponentId = options.components().stream().filter(c -> "Main".equals(c.slotName())).findFirst().orElseThrow().componentId();
        UUID tilapiaOptionId = options.components().stream().filter(c -> "Main".equals(c.slotName())).findFirst().orElseThrow()
                .alternatives().stream().filter(a -> "Tilapia".equals(a.label())).findFirst().orElseThrow().optionId();

        PackagePricingPreviewResponse preview = bookingService.previewPackagePricing(
                seed.businessId(), classicId, java.util.Map.of(mainComponentId, tilapiaOptionId), 8);

        // 440.00 base + 20.00 (Tilapia over Grilled Pepper Chicken) = 460.00 per guest * 8 = 3680.00
        assertEquals(0, new BigDecimal("460.00").compareTo(preview.perGuestPrice()));
        assertEquals(8, preview.partySize());
        assertEquals(0, new BigDecimal("3680.00").compareTo(preview.totalPrice()));
        assertEquals(0, new BigDecimal("2576.00").compareTo(preview.depositAmount())); // 70%
        assertEquals(0, new BigDecimal("1104.00").compareTo(preview.balanceAmount())); // 30%
    }

    @Test
    void exactlyOneBlockingReservationPolicyIsApplicableToBooking() {
        DemoSeedResponse seed = seedService.seedCafeBarNoir(true);
        List<PolicyEngine.PolicyContent> policies = policyEngine.applicablePolicyContent(seed.businessId(), "BOOKING_CREATE", null);
        assertEquals(1, policies.size());
        assertTrue(policies.get(0).requiresAcknowledgement());
        assertTrue(policies.get(0).blocksTransaction());
        assertTrue(policies.get(0).content().contains("70%"), "Policy content must contain the real supplied deposit terms");
    }

    @Test
    void menuAndPolicyKnowledgeEntriesAreSeeded() {
        DemoSeedResponse seed = seedService.seedCafeBarNoir(true);
        List<AiKnowledgeEntry> entries = aiKnowledgeEntryRepository.findAllByBusinessIdAndActiveTrueOrderByCreatedAtDesc(seed.businessId());
        assertTrue(entries.stream().anyMatch(e -> "MENU".equals(e.getCategory()) && "Main Course".equals(e.getTitle())));
        assertTrue(entries.stream().anyMatch(e -> "MENU".equals(e.getCategory()) && e.getContent().contains("Grilled Pepper Chicken — GH₵150")));
        assertTrue(entries.stream().anyMatch(e -> "POLICY".equals(e.getCategory())));
        assertTrue(entries.stream().anyMatch(e -> "PACKAGES".equals(e.getCategory())));
    }
}
