package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratel.rbms.dto.AvailabilityCheckResponse;
import com.ratel.rbms.dto.BookableServiceResponse;
import com.ratel.rbms.dto.BookingCreatedResponse;
import com.ratel.rbms.dto.BookingWidgetConfigResponse;
import com.ratel.rbms.dto.CreateBookingRequest;
import com.ratel.rbms.dto.CustomerResponse;
import com.ratel.rbms.entity.AiConversation;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.repository.BusinessRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * THE explicit AI tool allow-list. This is the only place a tool name from
 * the model is ever turned into a real action — dispatch below is a plain
 * switch over a fixed set of literal strings, never reflection, never a
 * dynamically-resolved class/method. A tool name that isn't one of the
 * cases in execute() cannot run, full stop; AiChatService checks
 * isRegistered() before ever calling execute() and logs anything else as
 * BLOCKED without touching this class at all.
 *
 * Every method here only ever calls an existing RBMS service method
 * (BookingService/CustomerService) — never a repository directly, and
 * never anything gated above what an anonymous public customer or a
 * business's own found/created Customer could already reach. No payment
 * mutation, no customer edit/delete, nothing outside this exact list.
 */
@Service
public class AiToolService {

    // The mandatory allow-list. Nothing outside this set can ever execute —
    // see isRegistered()/execute() below.
    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "getBusinessInfo",
            "getBusinessHours",
            "listBookableServices",
            "getServiceDetails",
            "checkAvailability",
            "findCustomer",
            "createCustomer",
            "createBooking",
            "escalateToStaff"
    );

    private final ObjectMapper objectMapper;
    private final BusinessRepository businessRepository;
    private final BookingService bookingService;
    private final CustomerService customerService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final AiConversationService aiConversationService;
    private final Validator validator;

    public AiToolService(
            ObjectMapper objectMapper,
            BusinessRepository businessRepository,
            BookingService bookingService,
            CustomerService customerService,
            NotificationService notificationService,
            ActivityLogService activityLogService,
            AiConversationService aiConversationService,
            Validator validator
    ) {
        this.objectMapper = objectMapper;
        this.businessRepository = businessRepository;
        this.bookingService = bookingService;
        this.customerService = customerService;
        this.notificationService = notificationService;
        this.activityLogService = activityLogService;
        this.aiConversationService = aiConversationService;
        this.validator = validator;
    }

    public boolean isRegistered(String toolName) {
        return ALLOWED_TOOLS.contains(toolName);
    }

    public List<AiToolDefinition> definitions() {
        return List.of(
                new AiToolDefinition("getBusinessInfo",
                        "Get this business's public name, industry, location, contact details and currency.",
                        "{\"type\":\"object\",\"properties\":{}}"),
                new AiToolDefinition("getBusinessHours",
                        "Get this business's configured working hours, day by day.",
                        "{\"type\":\"object\",\"properties\":{}}"),
                new AiToolDefinition("listBookableServices",
                        "List every service or package this business currently allows booking online, with price and description.",
                        "{\"type\":\"object\",\"properties\":{}}"),
                new AiToolDefinition("getServiceDetails",
                        "Get full details (price, description, whether it needs a location) for one specific bookable service or package by id.",
                        "{\"type\":\"object\",\"properties\":{\"serviceId\":{\"type\":\"string\",\"description\":\"The serviceCatalogId or packageId from listBookableServices.\"}},\"required\":[\"serviceId\"]}"),
                new AiToolDefinition("checkAvailability",
                        "Check whether a specific service/package can be booked at a specific date and time, given working hours and existing bookings.",
                        "{\"type\":\"object\",\"properties\":{"
                                + "\"serviceId\":{\"type\":\"string\",\"description\":\"The serviceCatalogId or packageId to check.\"},"
                                + "\"scheduledAt\":{\"type\":\"string\",\"description\":\"ISO-8601 date-time in UTC, e.g. 2026-08-27T14:00:00Z\"}"
                                + "},\"required\":[\"serviceId\",\"scheduledAt\"]}"),
                new AiToolDefinition("findCustomer",
                        "Look up an existing customer by phone number. Returns nothing found if there's no match — never guess or invent a customer.",
                        "{\"type\":\"object\",\"properties\":{\"phone\":{\"type\":\"string\",\"description\":\"The customer's phone number, any common format.\"}},\"required\":[\"phone\"]}"),
                new AiToolDefinition("createCustomer",
                        "Create a new customer record, or return the existing one if this phone number is already known. Never creates a duplicate.",
                        "{\"type\":\"object\",\"properties\":{"
                                + "\"fullName\":{\"type\":\"string\"},"
                                + "\"phone\":{\"type\":\"string\"},"
                                + "\"email\":{\"type\":\"string\"}"
                                + "},\"required\":[\"fullName\",\"phone\"]}"),
                new AiToolDefinition("createBooking",
                        "Create a real booking for a customer. Only call this after confirming the service, date/time, and that checkAvailability said it's available. Requires a valid phone number and email.",
                        "{\"type\":\"object\",\"properties\":{"
                                + "\"serviceId\":{\"type\":\"string\",\"description\":\"The serviceCatalogId or packageId being booked.\"},"
                                + "\"customerName\":{\"type\":\"string\"},"
                                + "\"customerPhone\":{\"type\":\"string\"},"
                                + "\"customerEmail\":{\"type\":\"string\"},"
                                + "\"scheduledAt\":{\"type\":\"string\",\"description\":\"ISO-8601 date-time in UTC, e.g. 2026-08-27T14:00:00Z\"},"
                                + "\"notes\":{\"type\":\"string\"}"
                                + "},\"required\":[\"serviceId\",\"customerName\",\"customerPhone\",\"customerEmail\",\"scheduledAt\"]}"),
                new AiToolDefinition("escalateToStaff",
                        "Hand this conversation off to a real staff member — use when the customer explicitly asks for a human, or you cannot safely help.",
                        "{\"type\":\"object\",\"properties\":{\"reason\":{\"type\":\"string\"}},\"required\":[]}")
        );
    }

    /** Executes exactly one registered tool. Never call with a name isRegistered() didn't already approve. */
    public ToolResult execute(UUID businessId, AiConversation conversation, String toolName, String argumentsJson) {
        JsonNode args;
        try {
            args = (argumentsJson == null || argumentsJson.isBlank())
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(argumentsJson);
        } catch (Exception e) {
            return ToolResult.failure("Malformed tool arguments.");
        }

        try {
            return switch (toolName) {
                case "getBusinessInfo" -> getBusinessInfo(businessId);
                case "getBusinessHours" -> getBusinessHours(businessId);
                case "listBookableServices" -> listBookableServices(businessId);
                case "getServiceDetails" -> getServiceDetails(businessId, args);
                case "checkAvailability" -> checkAvailability(businessId, args);
                case "findCustomer" -> findCustomer(conversation, args);
                case "createCustomer" -> createCustomer(conversation, args);
                case "createBooking" -> createBooking(businessId, conversation, args);
                case "escalateToStaff" -> escalateToStaff(businessId, conversation, args);
                // Unreachable in practice — AiChatService only ever calls
                // execute() after isRegistered() already returned true — but
                // fails closed rather than falling through if that ever changes.
                default -> ToolResult.failure("Unknown tool.");
            };
        } catch (com.ratel.rbms.exception.ApiException e) {
            return ToolResult.failure(e.getMessage());
        }
    }

    // ---- Read-only tools ----

    private ToolResult getBusinessInfo(UUID businessId) {
        Business business = businessRepository.findById(businessId).orElse(null);
        if (business == null) {
            return ToolResult.failure("Business not found.");
        }
        // Deliberately narrow — only ever the fields already safe to show a
        // customer (see Phase 0 reconnaissance §5's "public-safe" fields).
        // Never taxId, billing status, Paystack details, subscription plan.
        var info = objectMapper.createObjectNode();
        info.put("name", business.getName());
        info.put("industry", business.getIndustry() != null ? business.getIndustry().name() : null);
        info.put("location", business.getLocation());
        info.put("contactEmail", business.getContactEmail());
        info.put("contactPhone", business.getContactPhone());
        info.put("currency", business.getCurrency());
        return ToolResult.success(writeJson(info));
    }

    private ToolResult getBusinessHours(UUID businessId) {
        BookingWidgetConfigResponse config = bookingService.getWidgetConfig(businessId);
        return ToolResult.success(writeJson(config.workingHours()));
    }

    private ToolResult listBookableServices(UUID businessId) {
        List<BookableServiceResponse> services = bookingService.listBookableServices(businessId);
        return ToolResult.success(writeJson(services));
    }

    private ToolResult getServiceDetails(UUID businessId, JsonNode args) {
        UUID serviceId = parseUuid(args, "serviceId");
        if (serviceId == null) {
            return ToolResult.failure("serviceId is required and must be a valid id from listBookableServices.");
        }
        return bookingService.getBookableServiceDetail(businessId, serviceId)
                .map(detail -> ToolResult.success(writeJson(detail)))
                .orElseGet(() -> ToolResult.failure("No bookable service or package found with that id."));
    }

    private ToolResult checkAvailability(UUID businessId, JsonNode args) {
        UUID serviceId = parseUuid(args, "serviceId");
        Instant scheduledAt = parseInstant(args, "scheduledAt");
        if (serviceId == null || scheduledAt == null) {
            return ToolResult.failure("serviceId and a valid ISO-8601 scheduledAt are required.");
        }
        var detail = bookingService.getBookableServiceDetail(businessId, serviceId).orElse(null);
        if (detail == null) {
            return ToolResult.failure("No bookable service or package found with that id.");
        }
        AvailabilityCheckResponse result = bookingService.checkAvailability(
                businessId, detail.serviceCatalogId(), detail.packageId(), scheduledAt);
        return ToolResult.success(writeJson(result));
    }

    private ToolResult findCustomer(AiConversation conversation, JsonNode args) {
        String phone = args.path("phone").asText(null);
        if (phone == null || phone.isBlank()) {
            return ToolResult.failure("phone is required.");
        }
        return customerService.findByPhone(phone)
                .map(c -> {
                    aiConversationService.linkCustomer(conversation, c.id());
                    return ToolResult.success(writeJson(c));
                })
                .orElseGet(() -> ToolResult.success("{\"found\":false}"));
    }

    // ---- Controlled write tools ----

    private ToolResult createCustomer(AiConversation conversation, JsonNode args) {
        String fullName = args.path("fullName").asText(null);
        String phone = args.path("phone").asText(null);
        String email = args.path("email").asText(null);
        if (fullName == null || fullName.isBlank() || phone == null || phone.isBlank()) {
            return ToolResult.failure("fullName and phone are required.");
        }
        CustomerResponse customer = customerService.findOrCreate(fullName, phone, email, "AI");
        aiConversationService.linkCustomer(conversation, customer.id());
        return ToolResult.success(writeJson(customer), "CUSTOMER", customer.id());
    }

    private ToolResult createBooking(UUID businessId, AiConversation conversation, JsonNode args) {
        UUID serviceId = parseUuid(args, "serviceId");
        String customerName = args.path("customerName").asText(null);
        String customerPhone = args.path("customerPhone").asText(null);
        String customerEmail = args.path("customerEmail").asText(null);
        Instant scheduledAt = parseInstant(args, "scheduledAt");
        String notes = args.path("notes").asText(null);

        if (serviceId == null || customerName == null || customerPhone == null || scheduledAt == null) {
            return ToolResult.failure("serviceId, customerName, customerPhone and a valid ISO-8601 scheduledAt are required.");
        }

        var detail = bookingService.getBookableServiceDetail(businessId, serviceId).orElse(null);
        if (detail == null) {
            return ToolResult.failure("No bookable service or package found with that id.");
        }

        // Never reimplements phone validation, working-hours/blackout-date/
        // capacity validation, customer resolution, or pricing — all of
        // that happens exactly as it does for a public booking, inside
        // BookingService.createBooking() itself.
        CreateBookingRequest request = new CreateBookingRequest(
                detail.serviceCatalogId(), detail.packageId(), customerName, customerEmail, customerPhone,
                scheduledAt, notes, null
        );

        // Calling createBooking() as a plain Java method bypasses Spring MVC's
        // @Valid handling (that only fires on a controller parameter), so the
        // request's own @NotBlank/@Email annotations are validated explicitly
        // here — reusing those exact existing annotations, not hand-rolled
        // equivalents, to genuinely avoid duplicating validation logic.
        Set<ConstraintViolation<CreateBookingRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.iterator().next().getMessage();
            return ToolResult.failure(message);
        }

        BookingCreatedResponse created = bookingService.createBooking(businessId, request);

        // BookingService.createBooking() itself never writes an ActivityLog
        // entry (a public booking has no authenticated user to attribute it
        // to) — added here specifically for the AI path, per spec, rather
        // than changing that existing method's behavior for every booking.
        // userId is explicitly null (not TenantContext's logged-in tester),
        // matching the existing "null renders as System" convention.
        activityLogService.log(businessId, null,
                "Booking #" + created.bookingNumber() + " created by Tallia AI (" + conversation.getChannel() + ")",
                "BOOKING", null);

        return ToolResult.success(writeJson(created), "BOOKING", null);
    }

    // ---- Human escalation ----

    private ToolResult escalateToStaff(UUID businessId, AiConversation conversation, JsonNode args) {
        String reason = args.path("reason").asText("The customer asked to speak with a person.");
        aiConversationService.escalate(conversation);

        // Reuses the existing in-app notification pipeline exactly as-is —
        // this is what already fans out to Web Push automatically (see
        // NotificationService.create). No parallel notification system.
        notificationService.create(
                businessId,
                "AI_ESCALATION",
                "Tallia AI conversation needs you",
                reason,
                "AI_CONVERSATION",
                conversation.getId()
        );
        activityLogService.log(businessId, null,
                "Tallia AI escalated a conversation to staff (" + conversation.getChannel() + ")",
                "AI_CONVERSATION", conversation.getId());

        var result = objectMapper.createObjectNode();
        result.put("escalated", true);
        return ToolResult.success(writeJson(result), "AI_CONVERSATION", conversation.getId());
    }

    // ---- helpers ----

    private UUID parseUuid(JsonNode args, String field) {
        String raw = args.path(field).asText(null);
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant parseInstant(JsonNode args, String field) {
        String raw = args.path(field).asText(null);
        if (raw == null || raw.isBlank()) return null;
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't serialize tool result", e);
        }
    }

    /** Outcome of one tool execution — success/failure plus what to persist to ai_actions. */
    public record ToolResult(boolean success, String resultJson, String resultingEntityType, UUID resultingEntityId) {
        static ToolResult success(String resultJson) {
            return new ToolResult(true, resultJson, null, null);
        }

        static ToolResult success(String resultJson, String entityType, UUID entityId) {
            return new ToolResult(true, resultJson, entityType, entityId);
        }

        static ToolResult failure(String message) {
            return new ToolResult(false, "{\"error\":\"" + message.replace("\"", "'") + "\"}", null, null);
        }
    }
}
