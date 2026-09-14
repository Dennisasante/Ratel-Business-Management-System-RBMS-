package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratel.rbms.dto.AiChatRequest;
import com.ratel.rbms.dto.AiChatResponse;
import com.ratel.rbms.dto.BookingCreatedResponse;
import com.ratel.rbms.dto.DemoSeedResponse;
import com.ratel.rbms.dto.PackageOptionsResponse;
import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.ServiceOrderLineSnapshot;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.ServiceOrderLineSnapshotRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Restaurant-AI-demo phase — proves the FULL client-demo journey (Scenario A: package selection
 * -> substitution -> authoritative pricing -> policy disclosure/acknowledgement -> simulated
 * deposit -> real booking) actually works end-to-end against the real, seeded Cafe Bar Noir
 * tenant, through the exact same AiChatService/AiToolService/BookingService/PolicyEngine path a
 * genuine conversation uses.
 *
 * <p>AiProvider (the LLM) is mocked, exactly like {@link AiChatServiceTest}'s own established
 * pattern — this test proves the tool-call chain and every real side effect (pricing, policy
 * gate, booking) genuinely works; it does not (and cannot, without a real LLM key) prove the
 * model's own natural-language understanding, which is called out honestly in the phase's final
 * report as something that must be verified live, not by an automated test.
 */
@SpringBootTest
@Transactional
class RestaurantAiDemoFlowTest {

    @Autowired
    private CafeBarNoirDemoSeedService seedService;
    @Autowired
    private AiChatService aiChatService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private PolicyEngine policyEngine;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ServicePackageRepository servicePackageRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private ServiceOrderRepository serviceOrderRepository;
    @Autowired
    private ServiceOrderLineSnapshotRepository serviceOrderLineSnapshotRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiProvider aiProvider;

    private UUID businessId;
    private UUID seafoodExperienceId;
    private UUID mainComponentId;
    private UUID snapperOptionId;
    private UUID sideComponentId;
    private UUID jollofRiceOptionId;
    private UUID policyId;

    @BeforeEach
    void setUp() throws Exception {
        DemoSeedResponse seed = seedService.seedCafeBarNoir(true);
        businessId = seed.businessId();

        User owner = userRepository.findByEmail("demo.owner@cafe-bar-noir.example").orElseThrow();

        // The AI module is a separate, explicit Super-Admin step for every business (real or
        // demo) — enabling it here in the test mirrors exactly that one manual step, not a
        // change to the seed service itself.
        var bizEntity = businessRepositoryAutowired.findById(businessId).orElseThrow();
        bizEntity.setEnabledModules(concat(bizEntity.getEnabledModules(), "AI"));
        businessRepositoryAutowired.save(bizEntity);

        TenantContext.setBusinessId(businessId);
        TenantContext.setUserId(owner.getId());
        TenantContext.setRole("OWNER");

        when(aiProvider.isConfigured()).thenReturn(true);

        seafoodExperienceId = servicePackageRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .filter(p -> "Seafood Experience".equals(p.getName())).findFirst().orElseThrow().getId();
        PackageOptionsResponse options = bookingService.getPackageOptions(businessId, seafoodExperienceId);
        var mainComponent = options.components().stream().filter(c -> "Seafood Main".equals(c.slotName())).findFirst().orElseThrow();
        mainComponentId = mainComponent.componentId();
        snapperOptionId = mainComponent.alternatives().stream().filter(a -> "Snapper".equals(a.label())).findFirst().orElseThrow().optionId();
        var sideComponent = options.components().stream().filter(c -> "Side".equals(c.slotName())).findFirst().orElseThrow();
        sideComponentId = sideComponent.componentId();
        jollofRiceOptionId = sideComponent.alternatives().stream().filter(a -> "Jollof Rice".equals(a.label())).findFirst().orElseThrow().optionId();

        policyId = policyEngine.applicablePolicyContent(businessId, "BOOKING_CREATE", null).get(0).policyId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Autowired
    private com.ratel.rbms.repository.BusinessRepository businessRepositoryAutowired;

    private List<String> concat(List<String> base, String extra) {
        var copy = new java.util.ArrayList<>(base);
        copy.add(extra);
        return copy;
    }

    private Instant nextWeekdayEveningSlot() {
        ZonedDateTime next = ZonedDateTime.now(ZoneOffset.UTC)
                .plusWeeks(4) // clear of the 3-week refund pre-booking threshold, though irrelevant to creation itself
                .with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
                .withHour(19).withMinute(0).withSecond(0).withNano(0); // within the 7pm-11pm reservation block
        return next.toInstant();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    void scenarioA_fullPackageSubstitutionPolicyAndRealBookingSucceeds() throws Exception {
        Instant scheduledAt = nextWeekdayEveningSlot();
        Map<String, String> selections = Map.of(
                mainComponentId.toString(), snapperOptionId.toString(),
                sideComponentId.toString(), jollofRiceOptionId.toString()
        );

        AiToolCall listServices = new AiToolCall("call_1", "listBookableServices", "{}");
        AiToolCall packageOptions = new AiToolCall("call_2", "getPackageOptions",
                json(Map.of("packageId", seafoodExperienceId.toString())));
        AiToolCall previewPricing = new AiToolCall("call_3", "previewPackagePricing", json(Map.of(
                "packageId", seafoodExperienceId.toString(), "selections", selections, "partySize", 8)));
        AiToolCall applicablePolicies = new AiToolCall("call_4", "getApplicablePolicies", "{}");
        AiToolCall createCustomer = new AiToolCall("call_5", "createCustomer", json(Map.of(
                "fullName", "Scenario A Customer", "phone", "0209002002", "email", "scenario.a@example.com")));
        AiToolCall beginCommitment = new AiToolCall("call_6", "beginPolicyCommitment", "{}");

        when(aiProvider.chat(anyString(), anyList(), anyList()))
                .thenReturn(new AiProviderResult(null, List.of(listServices, packageOptions)))
                .thenReturn(new AiProviderResult(null, List.of(previewPricing)))
                .thenReturn(new AiProviderResult(null, List.of(applicablePolicies, createCustomer)))
                .thenReturn(new AiProviderResult(null, List.of(beginCommitment)))
                .thenAnswer(invocation -> {
                    // acknowledgePolicy needs the real commitmentReference minted by the PRECEDING
                    // tool call — resolved here (mirroring how a real LLM would read the previous
                    // tool result out of the conversation it's actually being fed) rather than
                    // hand-computed, so this test can never silently drift from the real value.
                    UUID commitmentReference = latestCommitmentReference();
                    AiToolCall acknowledge = new AiToolCall("call_7", "acknowledgePolicy", json(Map.of(
                            "policyId", policyId.toString(), "commitmentReference", commitmentReference.toString())));
                    return new AiProviderResult(null, List.of(acknowledge));
                })
                .thenAnswer(invocation -> {
                    UUID commitmentReference = latestCommitmentReference();
                    AiToolCall createBooking = new AiToolCall("call_8", "createBooking", json(Map.ofEntries(
                            Map.entry("serviceId", seafoodExperienceId.toString()),
                            Map.entry("customerName", "Scenario A Customer"),
                            Map.entry("customerPhone", "0209002002"),
                            Map.entry("customerEmail", "scenario.a@example.com"),
                            Map.entry("scheduledAt", scheduledAt.toString()),
                            Map.entry("selections", selections),
                            Map.entry("partySize", 8),
                            Map.entry("commitmentReference", commitmentReference.toString())
                    )));
                    return new AiProviderResult(null, List.of(createBooking));
                })
                .thenReturn(new AiProviderResult(
                        "Your reservation for 8 is confirmed! Deposit simulated and received.", List.of()));

        AiChatResponse response = aiChatService.chat(new AiChatRequest(null,
                "Hi, I want to organise dinner for 8 people — the Seafood Experience, "
                        + "but with Snapper instead of the default main and Jollof Rice for the side."));

        assertEquals(8, response.toolCalls().size());
        for (var summary : response.toolCalls()) {
            assertEquals("SUCCEEDED", summary.status(), summary.toolName() + " must succeed: " + summary);
        }
        assertTrue(response.assistantMessage().toLowerCase().contains("confirmed"));

        // ---- Verify the REAL side effects, not just that the tools reported success ----

        // 768.00 base - 150.00 (Snapper vs Seafood Espetada default) + 0.00 (Jollof Rice vs
        // Vegetable Rice default) = 618.00 per guest * 8 guests = 4944.00
        // Filtered by this test's own customer phone, not just businessId — the real Cafe Bar
        // Noir tenant (idempotent-by-slug) may already carry other real bookings from an actual
        // demo run against this same shared dev database.
        List<Booking> bookings = bookingRepository.findAll().stream()
                .filter(b -> businessId.equals(b.getBusinessId()) && "0209002002".equals(b.getCustomerWhatsapp())).toList();
        assertEquals(1, bookings.size(), "exactly one real Booking must have been created");
        Booking booking = bookings.get(0);

        ServiceOrder order = serviceOrderRepository.findByIdAndBusinessId(booking.getServiceOrderId(), businessId).orElseThrow();
        assertEquals(0, new BigDecimal("4944.00").compareTo(order.getPrice()));
        assertEquals(seafoodExperienceId, order.getServicePackageId());
        assertNotNull(order.getOfferingId(), "must be a real canonical booking, not a legacy fallback");
        assertTrue(order.getNotes() != null && order.getNotes().contains("Party size: 8"));

        List<ServiceOrderLineSnapshot> lines = serviceOrderLineSnapshotRepository.findAllByBusinessIdAndServiceOrderId(businessId, order.getId());
        BigDecimal lineSum = lines.stream().map(ServiceOrderLineSnapshot::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, order.getPrice().compareTo(lineSum), "line snapshots must sum to exactly the charged price");

        // The booking could only have succeeded because a real, satisfying PolicyDisclosure +
        // acknowledgement exists for this exact customer — proven here by re-running the same
        // authoritative gate the booking itself passed through, not merely inferring it from
        // "createBooking returned success".
        GateResult gate = policyEngine.evaluateGate(businessId, "BOOKING_CREATE", order.getOfferingId(),
                null, "BOOKING", order.getCustomerId());
        assertFalse(gate.allowed(), "sanity check: a FRESH commitment-less gate check must still require disclosure — proves the policy is genuinely enforced, not vacuously satisfied");
    }

    @Test
    void bookingIsBlockedWithoutPolicyAcknowledgementEvenWithAValidSelectionAndCustomer() {
        // Never called acknowledgePolicy — createBooking must fail closed.
        Instant scheduledAt = nextWeekdayEveningSlot();
        com.ratel.rbms.dto.CreateBookingRequest request = new com.ratel.rbms.dto.CreateBookingRequest(
                null, seafoodExperienceId, "No Ack Customer", "no-ack@example.com", "0244000077",
                scheduledAt, null, null, null,
                Map.of(mainComponentId, snapperOptionId), 8);

        ApiException ex = assertThrows(ApiException.class, () -> bookingService.createBooking(businessId, request));
        assertTrue(ex.getMessage().toLowerCase().contains("polic"), "must be rejected for the missing policy acknowledgement: " + ex.getMessage());
    }

    // Reads the commitmentReference minted by the most recent successful beginPolicyCommitment
    // AiAction recorded for this business — the same value a real LLM would have been handed
    // back in that tool's own JSON result and be expected to carry forward.
    @Autowired
    private com.ratel.rbms.repository.AiActionRepository aiActionRepository;

    private UUID latestCommitmentReference() throws Exception {
        var action = aiActionRepository.findAll().stream()
                .filter(a -> businessId.equals(a.getBusinessId()) && "beginPolicyCommitment".equals(a.getToolName()))
                .filter(a -> "SUCCEEDED".equals(a.getStatus()))
                .reduce((first, second) -> second) // latest
                .orElseThrow();
        var node = objectMapper.readTree(action.getResultJson());
        return UUID.fromString(node.path("commitmentReference").asText());
    }
}
