package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratel.rbms.entity.AiConversation;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Notification;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.repository.ActivityLogRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises AiToolService — the mandatory explicit allow-list — the same
 * way AiChatService would: never through reflection, only through
 * execute()'s fixed switch. Covers the spec's explicit test list: tool
 * allow-list, blocked tool execution, customer find/create dedupe, booking
 * tool delegation, and escalation's notification/activity-log side effects.
 */
@SpringBootTest
@Transactional
class AiToolServiceTest {

    @Autowired
    private AiToolService aiToolService;
    @Autowired
    private AiConversationService aiConversationService;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ServiceTypeRepository serviceTypeRepository;
    @Autowired
    private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ActivityLogRepository activityLogRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;

    private Business business;
    private AiConversation conversation;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        business = businessRepository.save(Business.builder()
                .name("AI Tool Test Business " + unique)
                .slug("ai-tool-test-" + unique)
                .industry(Industry.SALON)
                .currency("GHS")
                .build());

        // TenantContext.getUserId() always corresponds to a real logged-in
        // user in production (activity_logs.user_id has a real FK to
        // users) — a random unfollowed UUID here would violate that FK the
        // moment anything logs an action attributed to "whoever's using
        // the dashboard right now" (e.g. CustomerService.findOrCreate()).
        User owner = userRepository.save(User.builder()
                .businessId(business.getId())
                .fullName("Test Owner")
                .email("owner-" + unique + "@example.com")
                .build());

        TenantContext.setBusinessId(business.getId());
        TenantContext.setUserId(owner.getId());
        TenantContext.setRole("OWNER");

        conversation = aiConversationService.createConversation(business.getId(), "WEB_DEMO");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void unregisteredToolIsNeverExecuted() {
        assertFalse(aiToolService.isRegistered("deleteAllCustomers"));
        assertFalse(aiToolService.isRegistered("dropDatabase"));
        assertFalse(aiToolService.isRegistered("chargeMobileMoney"));
        assertFalse(aiToolService.isRegistered("markPaid"));
        assertFalse(aiToolService.isRegistered("refund"));

        // Even called directly (bypassing the isRegistered() gate a real
        // caller would check first), execute()'s own switch has no case for
        // this name and fails closed rather than doing anything.
        AiToolService.ToolResult result = aiToolService.execute(business.getId(), conversation, "deleteAllCustomers", "{}");
        assertFalse(result.success());
    }

    @Test
    void onlyTheNineSpecifiedToolsAreRegistered() {
        List<String> expected = List.of(
                "getBusinessInfo", "getBusinessHours", "listBookableServices", "getServiceDetails",
                "checkAvailability", "findCustomer", "createCustomer", "createBooking", "escalateToStaff"
        );
        for (String tool : expected) {
            assertTrue(aiToolService.isRegistered(tool), tool + " should be registered");
        }
        assertEquals(expected.size(), aiToolService.definitions().size());
    }

    @Test
    void createCustomerIsFindOrCreateNotDuplicate() throws Exception {
        String args = objectMapper.writeValueAsString(
                new java.util.HashMap<>(java.util.Map.of("fullName", "Ama Test", "phone", "0244555666")));

        AiToolService.ToolResult first = aiToolService.execute(business.getId(), conversation, "createCustomer", args);
        assertTrue(first.success());
        UUID firstId = first.resultingEntityId();
        assertNotNull(firstId);

        // Same phone, different name — must resolve to the SAME customer,
        // never a second record (this is the whole "tie by phone" premise).
        String secondArgs = objectMapper.writeValueAsString(
                new java.util.HashMap<>(java.util.Map.of("fullName", "Ama Different Spelling", "phone", "0244 555 666")));
        AiToolService.ToolResult second = aiToolService.execute(business.getId(), conversation, "createCustomer", secondArgs);
        assertTrue(second.success());
        assertEquals(firstId, second.resultingEntityId());
    }

    @Test
    void createCustomerRejectsAnObviouslyFakePhoneNumber() throws Exception {
        String args = objectMapper.writeValueAsString(
                new java.util.HashMap<>(java.util.Map.of("fullName", "Fake Person", "phone", "123")));
        AiToolService.ToolResult result = aiToolService.execute(business.getId(), conversation, "createCustomer", args);
        assertFalse(result.success());
    }

    @Test
    void findCustomerReturnsNotFoundRatherThanInventingSomeone() throws Exception {
        String args = objectMapper.writeValueAsString(java.util.Map.of("phone", "0209990000"));
        AiToolService.ToolResult result = aiToolService.execute(business.getId(), conversation, "findCustomer", args);
        assertTrue(result.success());
        assertTrue(result.resultJson().contains("\"found\":false"));
    }

    @Test
    void createBookingDelegatesToRealBookingServiceAndAppearsNormally() throws Exception {
        ServiceCatalogItem service = bookableTestService();
        Instant scheduledAt = nextWeekdayAt(10);

        String args = objectMapper.writeValueAsString(java.util.Map.of(
                "serviceId", service.getId().toString(),
                "customerName", "Booking Test Customer",
                "customerPhone", "0209991111",
                "customerEmail", "booking-test@example.com",
                "scheduledAt", scheduledAt.toString()
        ));

        AiToolService.ToolResult result = aiToolService.execute(business.getId(), conversation, "createBooking", args);
        assertTrue(result.success(), () -> "createBooking failed: " + result.resultJson());
        assertEquals("BOOKING", result.resultingEntityType());

        // The activity log line the spec explicitly asks for.
        boolean loggedForAi = activityLogRepository.findTop300ByBusinessIdOrderByCreatedAtDesc(business.getId()).stream()
                .anyMatch(a -> a.getAction() != null && a.getAction().contains("created by Tallia AI") && a.getUserId() == null);
        assertTrue(loggedForAi, "Expected an ActivityLog entry attributing the booking to Tallia AI with no userId");
    }

    @Test
    void createBookingRejectsAnInvalidPhoneNumberJustLikeThePublicForm() throws Exception {
        ServiceCatalogItem service = bookableTestService();
        Instant scheduledAt = nextWeekdayAt(11);

        String args = objectMapper.writeValueAsString(java.util.Map.of(
                "serviceId", service.getId().toString(),
                "customerName", "Bad Phone Customer",
                "customerPhone", "999",
                "customerEmail", "bad-phone@example.com",
                "scheduledAt", scheduledAt.toString()
        ));

        AiToolService.ToolResult result = aiToolService.execute(business.getId(), conversation, "createBooking", args);
        assertFalse(result.success());
    }

    @Test
    void createBookingRejectsOutsideWorkingHoursSameAsAnyOtherBooking() throws Exception {
        ServiceCatalogItem service = bookableTestService();
        // 3am on a weekday is outside the 9-6 default working hours.
        Instant scheduledAt = nextWeekdayAt(3);

        String args = objectMapper.writeValueAsString(java.util.Map.of(
                "serviceId", service.getId().toString(),
                "customerName", "Odd Hours Customer",
                "customerPhone", "0209992222",
                "customerEmail", "odd-hours@example.com",
                "scheduledAt", scheduledAt.toString()
        ));

        AiToolService.ToolResult result = aiToolService.execute(business.getId(), conversation, "createBooking", args);
        assertFalse(result.success());
    }

    @Test
    void checkAvailabilityReflectsRealCapacityWithoutActuallyBooking() throws Exception {
        ServiceCatalogItem service = bookableTestService();
        Instant scheduledAt = nextWeekdayAt(14);

        String args = objectMapper.writeValueAsString(java.util.Map.of(
                "serviceId", service.getId().toString(),
                "scheduledAt", scheduledAt.toString()
        ));
        AiToolService.ToolResult result = aiToolService.execute(business.getId(), conversation, "checkAvailability", args);
        assertTrue(result.success());
        assertTrue(result.resultJson().contains("\"available\":true"));
    }

    @Test
    void escalateToStaffMarksConversationEscalatedAndCreatesARealNotification() {
        AiToolService.ToolResult result = aiToolService.execute(
                business.getId(), conversation, "escalateToStaff", "{\"reason\":\"Customer asked for a manager\"}");
        assertTrue(result.success());

        AiConversation refreshed = aiConversationService.getOwnedEntity(conversation.getId(), business.getId());
        assertEquals("ESCALATED", refreshed.getStatus());

        List<Notification> notifications = notificationRepository.findTop50ByBusinessIdOrderByCreatedAtDesc(business.getId());
        assertTrue(notifications.stream().anyMatch(n -> "AI_ESCALATION".equals(n.getType())),
                "Expected an AI_ESCALATION notification — this is what the existing Web Push fan-out reads from");

        boolean logged = activityLogRepository.findTop300ByBusinessIdOrderByCreatedAtDesc(business.getId()).stream()
                .anyMatch(a -> a.getAction() != null && a.getAction().contains("escalated") && a.getUserId() == null);
        assertTrue(logged);
    }

    @Test
    void getBusinessInfoNeverExposesInternalBillingFields() {
        AiToolService.ToolResult result = aiToolService.execute(business.getId(), conversation, "getBusinessInfo", "{}");
        assertTrue(result.success());
        assertFalse(result.resultJson().toLowerCase().contains("taxid"));
        assertFalse(result.resultJson().toLowerCase().contains("paystack"));
        assertFalse(result.resultJson().toLowerCase().contains("billingstatus"));
    }

    private ServiceCatalogItem bookableTestService() {
        ServiceType type = serviceTypeRepository.save(ServiceType.builder()
                .businessId(business.getId())
                .name("Test Service Type")
                .build());
        return serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(business.getId())
                .serviceTypeId(type.getId())
                .name("Test Bookable Service")
                .price(new java.math.BigDecimal("50.00"))
                .active(true)
                .bookableOnline(true)
                .durationMinutes(30)
                .maxConcurrentBookings(1)
                .build());
    }

    // Always a real Mon-Sat business day well in the future, at a fixed UTC
    // hour within the 9-6 default working hours, regardless of when the
    // test suite happens to run.
    private Instant nextWeekdayAt(int hourUtc) {
        ZonedDateTime next = ZonedDateTime.now(ZoneOffset.UTC)
                .plusWeeks(2)
                .with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY))
                .withHour(hourUtc).withMinute(0).withSecond(0).withNano(0);
        return next.toInstant();
    }
}
