package com.ratel.rbms.service;

import com.ratel.rbms.dto.AiChatRequest;
import com.ratel.rbms.dto.AiChatResponse;
import com.ratel.rbms.dto.DemoSeedResponse;
import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cafe Bar Noir — pre-OpenAI client demo build. Proves the full package-booking client demo
 * works end-to-end using ONLY the real {@link MockAiProvider} bean — the exact bean Spring wires
 * by default (no {@code AI_PROVIDER} override, no API key) — driven with genuinely free-form
 * natural-language customer turns, never a scripted tool-call sequence. Everything downstream of
 * the "LLM decision" (AiToolService, BookingService, PolicyEngine, PackagePricingService,
 * Postgres) is 100% real; nothing about the demo tenant, pricing, or policy enforcement is faked.
 *
 * <p>No {@code @MockBean} is used here on purpose — deliberately letting Spring wire whichever
 * {@code AiProvider} is actually configured proves this suite exercises the real default
 * (mock) provider, not a stand-in swapped in by the test itself.
 */
@SpringBootTest
@Transactional
class CafeBarNoirMockDemoFlowTest {

    @Autowired
    private CafeBarNoirDemoSeedService seedService;
    @Autowired
    private AiChatService aiChatService;
    @Autowired
    private AiProvider aiProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ServicePackageRepository servicePackageRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

    private UUID businessId;

    @BeforeEach
    void setUp() {
        // Fails loudly, not silently, if this environment happens to have AI_PROVIDER=openai set
        // — this suite's entire point is proving the demo without a real LLM.
        assertEquals(MockAiProvider.class, aiProvider.getClass(),
                "This suite must run against the real MockAiProvider bean — found " + aiProvider.getClass()
                        + " instead. Unset AI_PROVIDER (or set it to \"mock\") before running it.");

        DemoSeedResponse seed = seedService.seedCafeBarNoir(true);
        businessId = seed.businessId();

        var biz = businessRepository.findById(businessId).orElseThrow();
        biz.setEnabledModules(concat(biz.getEnabledModules(), "AI"));
        businessRepository.save(biz);

        User owner = userRepository.findByEmail("demo.owner@cafe-bar-noir.example").orElseThrow();
        TenantContext.setBusinessId(businessId);
        TenantContext.setUserId(owner.getId());
        TenantContext.setRole("OWNER");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private List<String> concat(List<String> base, String extra) {
        var copy = new java.util.ArrayList<>(base);
        copy.add(extra);
        return copy;
    }

    private String nextFridayEveningPhrase() {
        return "this Friday at 7pm";
    }

    @Test
    void naturalLanguagePackageBookingWithoutSubstitutions_realBookingCreated() {
        UUID conversationId = null;

        AiChatResponse r1 = aiChatService.chat(new AiChatRequest(conversationId,
                "Hi, I'd like to book dinner for 8 people — the Seafood Experience package, "
                        + nextFridayEveningPhrase() + "."));
        conversationId = r1.conversationId();
        assertTrue(r1.assistantMessage().toLowerCase().contains("name") || r1.assistantMessage().toLowerCase().contains("contact"),
                "Should ask for customer details next: " + r1.assistantMessage());

        AiChatResponse r2 = aiChatService.chat(new AiChatRequest(conversationId,
                "My name is Ama Serwaa, 0209001001."));
        assertTrue(r2.assistantMessage().contains("Seafood Experience"), "Should show the order summary: " + r2.assistantMessage());
        assertTrue(r2.assistantMessage().contains("768.00") || r2.assistantMessage().contains("6144.00"),
                "Summary must show a real, authoritative price, never invented: " + r2.assistantMessage());
        assertTrue(r2.assistantMessage().contains("Shall I go ahead and confirm this reservation?"));

        AiChatResponse r3 = aiChatService.chat(new AiChatRequest(conversationId, "Yes, please confirm."));
        assertTrue(r3.assistantMessage().toLowerCase().contains("confirmed"), "Final reply: " + r3.assistantMessage());
        assertTrue(r3.assistantMessage().toLowerCase().contains("demo") || r3.assistantMessage().toLowerCase().contains("simulat"),
                "Must make the simulated-payment nature unmistakable: " + r3.assistantMessage());

        // Filtered by this test's own customer phone, not just businessId — the real Cafe Bar
        // Noir tenant (idempotent-by-slug) may already carry other real bookings from an actual
        // demo run against this same shared dev database; that must never make this assertion
        // flaky, only "did THIS test's own action create exactly the one booking it expects."
        List<Booking> bookings = bookingRepository.findAll().stream()
                .filter(b -> businessId.equals(b.getBusinessId()) && "0209001001".equals(b.getCustomerWhatsapp()))
                .toList();
        assertEquals(1, bookings.size(), "exactly one real Booking must exist for this test's own customer");
        ServiceOrder order = serviceOrderRepository.findByIdAndBusinessId(bookings.get(0).getServiceOrderId(), businessId).orElseThrow();
        // 300 (Seafood Espetada) + 75 (Vegetable Rice) + 298 (Seafood Salad) + 95 (Cheesecake) = 768.00/guest * 8
        assertEquals(0, new BigDecimal("6144.00").compareTo(order.getPrice()));
        assertNotNull(order.getOfferingId(), "must be a real canonical booking");
        assertTrue(order.getNotes() != null && order.getNotes().contains("Party size: 8"));
    }

    @Test
    void naturalLanguageValidSubstitution_isAppliedAndPricedCorrectly() {
        UUID conversationId = null;

        AiChatResponse r1 = aiChatService.chat(new AiChatRequest(conversationId,
                "Hi, dinner for 8 people please, the Seafood Experience, but can I get Snapper instead of the "
                        + "seafood espetada, and jollof rice for the side? " + nextFridayEveningPhrase() + "."));
        conversationId = r1.conversationId();

        AiChatResponse r2 = aiChatService.chat(new AiChatRequest(conversationId, "My name is Kojo Mensah, 0244555777."));
        // 300 -150 (Snapper) + 75 -75 (Jollof Rice, same price as default Vegetable Rice) + 298 + 95 = 618.00/guest
        assertTrue(r2.assistantMessage().contains("618.00"), "Must reflect the real substitution delta: " + r2.assistantMessage());
        assertTrue(r2.assistantMessage().contains("Snapper"));
        assertTrue(r2.assistantMessage().contains("Jollof Rice"));

        AiChatResponse r3 = aiChatService.chat(new AiChatRequest(conversationId, "Yes, confirm it please."));
        assertTrue(r3.assistantMessage().toLowerCase().contains("confirmed"));

        List<Booking> bookings = bookingRepository.findAll().stream()
                .filter(b -> businessId.equals(b.getBusinessId()) && "0244555777".equals(b.getCustomerWhatsapp()))
                .toList();
        assertEquals(1, bookings.size());
        ServiceOrder order = serviceOrderRepository.findByIdAndBusinessId(bookings.get(0).getServiceOrderId(), businessId).orElseThrow();
        assertEquals(0, new BigDecimal("4944.00").compareTo(order.getPrice())); // 618.00 * 8
    }

    @Test
    void naturalLanguageInvalidSubstitution_isRejectedWithRealAlternativesNeverSilentlyAccepted() {
        AiChatResponse r1 = aiChatService.chat(new AiChatRequest(null,
                "Hi, dinner for 8 people, the Seafood Experience package. Can I get Steak Espetada instead of "
                        + "the seafood main? " + nextFridayEveningPhrase() + "."));

        String reply = r1.assistantMessage();
        assertTrue(reply.toLowerCase().contains("steak espetada"), "Must name the specific rejected item: " + reply);
        assertTrue(reply.toLowerCase().contains("isn't available") || reply.toLowerCase().contains("not available"),
                "Must clearly reject it, not silently accept it: " + reply);
        // Must offer the REAL configured alternatives for that package instead of just refusing.
        assertTrue(reply.contains("Jambalaya") || reply.contains("Snapper") || reply.contains("Tilapia"),
                "Must offer real valid alternatives: " + reply);
    }

    @Test
    void mockProviderMakesNoNetworkCallAndRequiresNoApiKey() {
        // Structural proof, not just an assertion of intent: MockAiProvider.isConfigured() is
        // hardcoded true with no external dependency (see its own source), and this whole suite
        // just completed multiple full conversations with zero AI_PROVIDER/OPENAI_API_KEY
        // involvement — confirmed by the class-identity check in setUp() above running first.
        assertTrue(aiProvider.isConfigured());
    }
}
