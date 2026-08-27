package com.ratel.rbms.service;

import com.ratel.rbms.dto.AiChatResponse;
import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.AiConversation;
import com.ratel.rbms.entity.AiMessage;
import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Notification;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.AiActionRepository;
import com.ratel.rbms.repository.AiChannelBindingRepository;
import com.ratel.rbms.repository.AiConversationRepository;
import com.ratel.rbms.repository.AiMessageRepository;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.NotificationRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tallia AI Phase 3A — channel foundation & AI hardening. Covers the spec's
 * own minimum test list (§32/§33): channel routing, tenant isolation for
 * bindings, binding uniqueness, external-message idempotency (the critical
 * "no duplicate booking" test), conversation identity, AI-core independence
 * from channel-specific shapes, escalation retaining channel context,
 * message-size limits, and bounded conversation history.
 *
 * Uses a mocked AiProvider exactly like AiChatServiceTest — real
 * AiToolService/BookingService underneath, so a scripted "create a booking"
 * turn exercises the actual booking pipeline, not a stub.
 */
@SpringBootTest
@Transactional
class AiChannelFoundationTest {

    @Autowired
    private AiChannelRouter aiChannelRouter;
    @Autowired
    private AiChatService aiChatService;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AiChannelBindingRepository aiChannelBindingRepository;
    @Autowired
    private AiConversationRepository aiConversationRepository;
    @Autowired
    private AiMessageRepository aiMessageRepository;
    @Autowired
    private AiActionRepository aiActionRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ServiceTypeRepository serviceTypeRepository;
    @Autowired
    private ServiceCatalogItemRepository serviceCatalogItemRepository;

    @MockBean
    private AiProvider aiProvider;

    private Business business;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        business = businessRepository.save(Business.builder()
                .name("Channel Foundation Test Business " + unique)
                .slug("channel-foundation-" + unique)
                .industry(Industry.SALON)
                .currency("GHS")
                .build());
        business.setEnabledModules(concat(business.getEnabledModules(), "AI"));
        business = businessRepository.save(business);

        User owner = userRepository.save(User.builder()
                .businessId(business.getId())
                .fullName("Test Owner")
                .email("channel-owner-" + unique + "@example.com")
                .build());

        TenantContext.setBusinessId(business.getId());
        TenantContext.setUserId(owner.getId());
        TenantContext.setRole("OWNER");

        when(aiProvider.isConfigured()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // Channel routing / tenant isolation
    // ------------------------------------------------------------------

    @Test
    void webDemoRoutesUsingOnlyTenantContextAndCreatesAWebDemoConversation() {
        when(aiProvider.chat(anyString(), anyList(), anyList()))
                .thenReturn(new AiProviderResult("Hello!", List.of()));

        AiChatResponse response = aiChannelRouter.routeWebDemo(null, "Hi there");

        AiConversation conversation = aiConversationRepository.findByIdAndBusinessId(response.conversationId(), business.getId()).orElseThrow();
        assertEquals("WEB_DEMO", conversation.getChannel());
        assertNull(conversation.getChannelBindingId());
        assertNull(conversation.getExternalConversationId());
    }

    @Test
    void aChannelBindingForBusinessANeverResolvesToBusinessB() {
        Business businessB = businessRepository.save(Business.builder()
                .name("Other Business " + UUID.randomUUID())
                .slug("other-biz-" + UUID.randomUUID().toString().substring(0, 8))
                .industry(Industry.OTHER)
                .build());
        businessB.setEnabledModules(concat(businessB.getEnabledModules(), "AI"));
        businessRepository.save(businessB);

        AiChannelBinding bindingForA = aiChannelBindingRepository.save(AiChannelBinding.builder()
                .businessId(business.getId())
                .channel(AiChannel.WHATSAPP)
                .externalAccountId("waba-account-" + UUID.randomUUID())
                .active(true)
                .build());

        when(aiProvider.chat(anyString(), anyList(), anyList()))
                .thenReturn(new AiProviderResult("Answer for the resolved business.", List.of()));

        IncomingAiMessage message = new IncomingAiMessage(
                AiChannel.WHATSAPP, null, "ext-conv-1", "ext-msg-1", "ext-user-1",
                "Hello from WhatsApp", Instant.now(), null);

        AiChatResponse response = aiChannelRouter.routeExternal(message, bindingForA.getExternalAccountId());

        AiConversation conversation = aiConversationRepository.findById(response.conversationId()).orElseThrow();
        assertEquals(business.getId(), conversation.getBusinessId());
        assertNotEquals(businessB.getId(), conversation.getBusinessId());
    }

    @Test
    void anUnknownExternalAccountIdIsRejectedNotGuessedAtAsSomeBusiness() {
        IncomingAiMessage message = new IncomingAiMessage(
                AiChannel.WHATSAPP, null, "ext-conv-x", "ext-msg-x", "ext-user-x",
                "Hello", Instant.now(), null);

        assertThrows(ApiException.class, () -> aiChannelRouter.routeExternal(message, "no-such-account-id"));
    }

    @Test
    void duplicateExternalAccountIdForTheSameChannelIsRejectedByTheBindingUniquenessConstraint() {
        Business businessB = businessRepository.save(Business.builder()
                .name("Second Business " + UUID.randomUUID())
                .slug("second-biz-" + UUID.randomUUID().toString().substring(0, 8))
                .industry(Industry.OTHER)
                .build());
        businessRepository.save(businessB);

        String sharedExternalAccountId = "shared-waba-id-" + UUID.randomUUID();
        aiChannelBindingRepository.save(AiChannelBinding.builder()
                .businessId(business.getId())
                .channel(AiChannel.WHATSAPP)
                .externalAccountId(sharedExternalAccountId)
                .active(true)
                .build());

        AiChannelBinding duplicate = AiChannelBinding.builder()
                .businessId(businessB.getId())
                .channel(AiChannel.WHATSAPP)
                .externalAccountId(sharedExternalAccountId)
                .active(true)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            aiChannelBindingRepository.save(duplicate);
            aiChannelBindingRepository.flush();
        });
    }

    @Test
    void sameExternalConversationIdOnTheSameBindingAlwaysResolvesToTheSameAiConversation() {
        AiChannelBinding binding = aiChannelBindingRepository.save(AiChannelBinding.builder()
                .businessId(business.getId())
                .channel(AiChannel.WHATSAPP)
                .externalAccountId("waba-" + UUID.randomUUID())
                .active(true)
                .build());

        when(aiProvider.chat(anyString(), anyList(), anyList()))
                .thenReturn(new AiProviderResult("First reply.", List.of()))
                .thenReturn(new AiProviderResult("Second reply.", List.of()));

        IncomingAiMessage first = new IncomingAiMessage(
                AiChannel.WHATSAPP, null, "same-external-conversation", "msg-1", "user-1",
                "First message", Instant.now(), null);
        IncomingAiMessage second = new IncomingAiMessage(
                AiChannel.WHATSAPP, null, "same-external-conversation", "msg-2", "user-1",
                "Second message", Instant.now(), null);

        AiChatResponse r1 = aiChannelRouter.routeExternal(first, binding.getExternalAccountId());
        AiChatResponse r2 = aiChannelRouter.routeExternal(second, binding.getExternalAccountId());

        assertEquals(r1.conversationId(), r2.conversationId());
    }

    @Test
    void differentExternalConversationIdsResolveToSeparateAiConversations() {
        AiChannelBinding binding = aiChannelBindingRepository.save(AiChannelBinding.builder()
                .businessId(business.getId())
                .channel(AiChannel.WHATSAPP)
                .externalAccountId("waba-" + UUID.randomUUID())
                .active(true)
                .build());

        when(aiProvider.chat(anyString(), anyList(), anyList()))
                .thenReturn(new AiProviderResult("First reply.", List.of()))
                .thenReturn(new AiProviderResult("Second reply.", List.of()));

        IncomingAiMessage first = new IncomingAiMessage(
                AiChannel.WHATSAPP, null, "external-conversation-a", "msg-1", "user-1",
                "First message", Instant.now(), null);
        IncomingAiMessage second = new IncomingAiMessage(
                AiChannel.WHATSAPP, null, "external-conversation-b", "msg-2", "user-2",
                "Second message", Instant.now(), null);

        AiChatResponse r1 = aiChannelRouter.routeExternal(first, binding.getExternalAccountId());
        AiChatResponse r2 = aiChannelRouter.routeExternal(second, binding.getExternalAccountId());

        assertNotEquals(r1.conversationId(), r2.conversationId());
    }

    // ------------------------------------------------------------------
    // §33 — the critical duplicate-external-message / no-duplicate-booking test
    // ------------------------------------------------------------------

    @Test
    void theSameExternalMessageProcessedTwiceNeverCreatesASecondBookingOrNotification() throws Exception {
        ServiceCatalogItem service = bookableTestService();
        Instant scheduledAt = nextWeekdayAt(10);

        AiChannelBinding binding = aiChannelBindingRepository.save(AiChannelBinding.builder()
                .businessId(business.getId())
                .channel(AiChannel.WHATSAPP)
                .externalAccountId("waba-" + UUID.randomUUID())
                .active(true)
                .build());

        String argsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                "serviceId", service.getId().toString(),
                "customerName", "Duplicate Message Customer",
                "customerPhone", "0209993333",
                "customerEmail", "dup-message@example.com",
                "scheduledAt", scheduledAt.toString()
        ));
        AiToolCall bookingCall = new AiToolCall("call_1", "createBooking", argsJson);

        // Scripted so a FRESH (non-idempotent) run would call the provider
        // twice: once producing the tool call, once producing the final
        // answer. If idempotency ever regresses and this turn is
        // reprocessed, Mockito's default single-stub-then-repeat-last
        // behavior means the SECOND processTurn call would try to create a
        // second booking with the exact same tool call again — exactly what
        // this test must prove never happens.
        when(aiProvider.chat(anyString(), anyList(), anyList()))
                .thenReturn(new AiProviderResult(null, List.of(bookingCall)))
                .thenReturn(new AiProviderResult("Booked! See you Wednesday.", List.of()));

        IncomingAiMessage message = new IncomingAiMessage(
                AiChannel.WHATSAPP, null, "booking-conversation", "book-me-saturday-msg",
                "external-customer-1", "Book me for Saturday.", Instant.now(), null);

        AiChatResponse first = aiChannelRouter.routeExternal(message, binding.getExternalAccountId());
        int notificationCountAfterFirst = notificationRepository.findTop50ByBusinessIdOrderByCreatedAtDesc(business.getId()).size();
        int bookingCountAfterFirst = bookingRepository.findAllByBusinessIdOrderByCreatedAtDesc(business.getId()).size();
        assertEquals(1, bookingCountAfterFirst);

        // Re-deliver the SAME external message (same externalMessageId,
        // same conversation) — simulating a webhook retry.
        AiChatResponse second = aiChannelRouter.routeExternal(message, binding.getExternalAccountId());

        assertEquals(first.conversationId(), second.conversationId());
        assertEquals(first.assistantMessage(), second.assistantMessage());

        int bookingCountAfterSecond = bookingRepository.findAllByBusinessIdOrderByCreatedAtDesc(business.getId()).size();
        int notificationCountAfterSecond = notificationRepository.findTop50ByBusinessIdOrderByCreatedAtDesc(business.getId()).size();
        assertEquals(1, bookingCountAfterSecond, "The exact same external message must never create a second booking");
        assertEquals(notificationCountAfterFirst, notificationCountAfterSecond, "No duplicate booking notification");

        // Exactly one USER message persisted for this external_message_id —
        // the second delivery never appended another.
        List<AiMessage> userMessages = aiMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(first.conversationId()).stream()
                .filter(m -> "USER".equals(m.getRole()))
                .toList();
        assertEquals(1, userMessages.size());

        // Only one AiAction (STARTED->SUCCEEDED) for createBooking — the
        // tool was never re-executed on the replay.
        long createBookingActions = aiActionRepository.findAllByConversationIdOrderByCreatedAtAsc(first.conversationId()).stream()
                .filter(a -> "createBooking".equals(a.getToolName()))
                .count();
        assertEquals(1, createBookingActions);

        // The provider was only ever called twice total (the original
        // turn's tool-call round + final-answer round) — never a third
        // time for the replay.
        org.mockito.Mockito.verify(aiProvider, org.mockito.Mockito.times(2)).chat(anyString(), anyList(), anyList());
    }

    // ------------------------------------------------------------------
    // Escalation retains channel context
    // ------------------------------------------------------------------

    @Test
    void escalationThroughAnExternalChannelRetainsChannelContextOnTheConversationAndAction() {
        AiChannelBinding binding = aiChannelBindingRepository.save(AiChannelBinding.builder()
                .businessId(business.getId())
                .channel(AiChannel.WHATSAPP)
                .externalAccountId("waba-" + UUID.randomUUID())
                .active(true)
                .build());

        AiToolCall escalateCall = new AiToolCall("call_1", "escalateToStaff", "{\"reason\":\"Needs a human\"}");
        when(aiProvider.chat(anyString(), anyList(), anyList()))
                .thenReturn(new AiProviderResult(null, List.of(escalateCall)))
                .thenReturn(new AiProviderResult("Connecting you with our team now.", List.of()));

        IncomingAiMessage message = new IncomingAiMessage(
                AiChannel.WHATSAPP, null, "escalation-conversation", "escalation-msg-1", "external-user-9",
                "I want to speak to a manager.", Instant.now(), null);

        AiChatResponse response = aiChannelRouter.routeExternal(message, binding.getExternalAccountId());

        AiConversation conversation = aiConversationRepository.findById(response.conversationId()).orElseThrow();
        assertEquals("ESCALATED", conversation.getStatus());
        assertEquals("WHATSAPP", conversation.getChannel());
        assertEquals(binding.getId(), conversation.getChannelBindingId());

        boolean escalateActionHasChannelContext = aiActionRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .anyMatch(a -> "escalateToStaff".equals(a.getToolName())
                        && "WHATSAPP".equals(a.getChannel())
                        && binding.getId().equals(a.getChannelBindingId()));
        assertTrue(escalateActionHasChannelContext, "Expected the escalateToStaff AiAction to retain its channel context");
    }

    // ------------------------------------------------------------------
    // Message / history limits
    // ------------------------------------------------------------------

    @Test
    void anOversizedMessageIsRejectedServerSideRegardlessOfChannel() {
        AiConversation conversation = aiConversationRepository.save(AiConversation.builder()
                .businessId(business.getId())
                .channel("WEB_DEMO")
                .status("ACTIVE")
                .startedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build());

        String oversized = "x".repeat(4001);
        assertThrows(ApiException.class, () -> aiChatService.processTurn(business.getId(), conversation, oversized, null));
    }

    @Test
    void conversationHistoryBeyondTheBoundedWindowIsNeverSentToTheProvider() {
        AiConversation conversation = aiConversationRepository.save(AiConversation.builder()
                .businessId(business.getId())
                .channel("WEB_DEMO")
                .status("ACTIVE")
                .startedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build());

        // 50 prior USER/ASSISTANT messages already on this conversation —
        // well past the bounded window AiChatService caps provider history
        // to (persisted history itself is never trimmed).
        for (int i = 0; i < 50; i++) {
            aiMessageRepository.save(AiMessage.builder()
                    .businessId(business.getId())
                    .conversationId(conversation.getId())
                    .role(i % 2 == 0 ? "USER" : "ASSISTANT")
                    .content("Message " + i)
                    .build());
        }
        assertEquals(50, aiMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.getId()).size());

        org.mockito.ArgumentCaptor<List<AiProviderMessage>> historyCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        when(aiProvider.chat(anyString(), historyCaptor.capture(), anyList()))
                .thenReturn(new AiProviderResult("Final answer.", List.of()));

        aiChatService.processTurn(business.getId(), conversation, "One more message", null);

        // Full history is still there (nothing was deleted) — the original
        // 50 plus this turn's own USER and ASSISTANT messages.
        assertEquals(52, aiMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.getId()).size());
        // ...but the provider was only ever handed a bounded window of it.
        assertTrue(historyCaptor.getValue().size() < 52, "Expected the provider-facing history to be capped, not the full 52 messages");
    }

    // ------------------------------------------------------------------
    // Credential security
    // ------------------------------------------------------------------

    @Test
    void channelStatusResponseNeverExposesAnyCredentialField() {
        // Structural guarantee: AiChannelStatusResponse — the one channel-
        // related payload any authenticated business user can fetch today
        // — simply has no field capable of carrying a credential. Every
        // other channel-binding field (credentialsEncrypted) never leaves
        // AiChannelBinding at all; no controller/DTO in this phase reads it.
        var fields = com.ratel.rbms.dto.AiChannelStatusResponse.class.getDeclaredFields();
        for (var field : fields) {
            String name = field.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(name.contains("credential") || name.contains("secret") || name.contains("token"),
                    "AiChannelStatusResponse must never expose a credential-shaped field, found: " + field.getName());
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private ServiceCatalogItem bookableTestService() {
        ServiceType type = serviceTypeRepository.save(ServiceType.builder()
                .businessId(business.getId())
                .name("Test Service Type")
                .build());
        return serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(business.getId())
                .serviceTypeId(type.getId())
                .name("Test Bookable Service")
                .price(new BigDecimal("50.00"))
                .active(true)
                .bookableOnline(true)
                .durationMinutes(30)
                .maxConcurrentBookings(1)
                .build());
    }

    private Instant nextWeekdayAt(int hourUtc) {
        ZonedDateTime next = ZonedDateTime.now(ZoneOffset.UTC)
                .plusWeeks(2)
                .with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY))
                .withHour(hourUtc).withMinute(0).withSecond(0).withNano(0);
        return next.toInstant();
    }

    private List<String> concat(List<String> base, String extra) {
        var copy = new java.util.ArrayList<>(base);
        copy.add(extra);
        return copy;
    }
}
