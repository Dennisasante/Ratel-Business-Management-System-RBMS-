package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratel.rbms.util.PhoneUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Development/demo AiProvider — a small deterministic intent + slot-filling
 * engine standing in for a real LLM, so the whole Customer/Test Chat ->
 * AiChatService -> AiToolService -> RBMS loop can be demonstrated and
 * tested with no OpenAI call. Registered instead of OpenAiProvider when
 * app.ai.provider is "mock" or unset (see application.yml) — never both
 * beans exist at once.
 *
 * CRITICAL: this class talks to the outside world through the exact same
 * contract OpenAiProvider does — it returns AiToolCall requests the same
 * way, and AiChatService dispatches them through AiToolService's allow-list
 * exactly the same way. This class never touches a repository or an RBMS
 * service directly; it only ever asks for one of the registered tools by
 * name, the same as a real model would. It also carries no knowledge of
 * any specific business (no "Paradise Beach Resort" anywhere in this file)
 * — everything it knows about a business comes from parsing the same
 * systemPrompt string a real model would receive (built once, centrally,
 * in AiChatService), so it works identically for any business's data.
 */
@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {

    private final ObjectMapper objectMapper;

    public MockAiProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured() {
        // Nothing external to misconfigure — always usable.
        return true;
    }

    private enum Intent { HUMAN_HANDOFF, EVENT_LEAD, BOOKING, AVAILABILITY, SERVICE_INFO, GENERAL_INFO }

    @Override
    public AiProviderResult chat(String systemPrompt, List<AiProviderMessage> conversation, List<AiToolDefinition> tools) {
        SystemPromptFacts facts = parseSystemPrompt(systemPrompt);
        String latest = lastUserMessage(conversation);
        if (latest == null) {
            return textResult("Hi there! How can I help you today?");
        }

        Map<String, JsonNode> toolResults = toolResultsSinceLastUserMessage(conversation);
        String fullUserText = allUserText(conversation);
        String priorUserText = allUserTextExceptLatest(conversation);

        Intent intent = classify(latest, priorUserText);

        return switch (intent) {
            case HUMAN_HANDOFF -> handleHandoff(facts, toolResults, latest);
            case EVENT_LEAD -> handleEventLead(facts, toolResults, latest);
            case BOOKING, AVAILABILITY -> handleBookingFlow(facts, toolResults, fullUserText, latest, intent == Intent.BOOKING);
            case SERVICE_INFO -> handleServiceInfo(facts, toolResults, fullUserText);
            case GENERAL_INFO -> handleGeneralInfo(facts, toolResults, fullUserText);
        };
    }

    // ------------------------------------------------------------------
    // Intent classification — a bare keyword router, deliberately small.
    // lastAssistant lets a short follow-up ("6 of us", a bare phone number,
    // "yes") continue whatever flow the previous turn was clearly steering
    // toward, rather than being reclassified as GENERAL_INFO every time.
    // ------------------------------------------------------------------

    // priorUserText is every earlier customer turn (never including the
    // latest one) — used to recognize "we're already mid-booking" from what
    // the CUSTOMER has said so far, not by grepping this mock's own
    // previously-generated reply for specific marker phrases. That
    // alternative (matching against the assistant's own wording) is
    // fragile by construction: it silently breaks the moment a reply's
    // phrasing changes, exactly as happened here during development — a
    // short follow-up like "6 of us" or a bare phone number has no
    // booking/availability keyword of its own, so classification for a
    // continuation turn has to come from what the conversation has already
    // established, not from the current turn in isolation.
    private Intent classify(String latest, String priorUserText) {
        String t = latest.toLowerCase(Locale.ROOT);

        if (containsAny(t, "speak to someone", "speak with someone", "talk to someone", "talk with someone",
                "talk to a person", "speak to a person", "talk to a manager", "speak to a manager",
                "member of staff", "a staff member", "supervisor", "human", "real person",
                "complaint", "complain", "customer service")) {
            return Intent.HUMAN_HANDOFF;
        }
        if (containsAny(t, "birthday party", "wedding", "corporate event", "anniversary", "planning an event",
                "organi", "party for", "event for", "group event")) {
            return Intent.EVENT_LEAD;
        }

        boolean bookingWord = containsAny(t, "book", "reserve", "reservation");
        // "visit"/"come to"/"come by" are how a customer naturally opens a
        // booking-shaped request without ever saying "book" or "available"
        // outright (see the spec's own example: "I want to visit the beach
        // this Saturday") — treated as availability-shaped intent whenever
        // paired with any date/time reference, so it isn't misread as a
        // plain general question.
        boolean availabilityWord = containsAny(t, "available", "availability", "space for", "room for",
                "do you have space", "can i come", "can i visit", "free on", "free at")
                || (containsAny(t, "visit", "come to", "come by", "come in", "stop by") && mentionsDateOrTime(t));

        if (bookingWord) return Intent.BOOKING;
        if (availabilityWord) return Intent.AVAILABILITY;

        // A short follow-up turn ("6 of us", a bare phone number, "yes") —
        // if the customer has already engaged a booking/availability
        // conversation earlier in this same thread, stay on that thread
        // rather than re-classifying from this one short turn alone.
        String priorLower = priorUserText == null ? "" : priorUserText.toLowerCase(Locale.ROOT);
        boolean priorEngagement = containsAny(priorLower, "book", "reserve", "reservation", "available", "availability")
                || mentionsDateOrTime(priorLower);
        if (priorEngagement) return Intent.BOOKING;

        if (containsAny(t, "how much", "cost", "price", "tell me about", "what packages", "package",
                "cabana", "day pass")) {
            return Intent.SERVICE_INFO;
        }
        return Intent.GENERAL_INFO;
    }

    // ------------------------------------------------------------------
    // HUMAN_HANDOFF
    // ------------------------------------------------------------------

    private AiProviderResult handleHandoff(SystemPromptFacts facts, Map<String, JsonNode> toolResults, String latest) {
        if (!toolResults.containsKey("escalateToStaff")) {
            return toolCallResult("escalateToStaff", Map.of("reason", "Customer request: \"" + latest + "\""));
        }
        String message = facts.humanHandoffMessage != null
                ? facts.humanHandoffMessage
                : "I'll connect you with a member of our team who can assist you.";
        return textResult(message);
    }

    // ------------------------------------------------------------------
    // EVENT_LEAD — a lightweight lead-capture path: escalate with the
    // enquiry detail attached, then respond warmly rather than trying to
    // plan a wedding/birthday itself.
    // ------------------------------------------------------------------

    private AiProviderResult handleEventLead(SystemPromptFacts facts, Map<String, JsonNode> toolResults, String latest) {
        if (!toolResults.containsKey("escalateToStaff")) {
            return toolCallResult("escalateToStaff", Map.of("reason", "Event enquiry: \"" + latest + "\""));
        }
        return textResult("That sounds wonderful! I've passed your event enquiry to our events team, and someone "
                + "will be in touch shortly to help plan the details.");
    }

    // ------------------------------------------------------------------
    // SERVICE_INFO — ground the answer in the real service catalogue,
    // never an invented price.
    // ------------------------------------------------------------------

    private AiProviderResult handleServiceInfo(SystemPromptFacts facts, Map<String, JsonNode> toolResults, String fullUserText) {
        JsonNode services = toolResults.get("listBookableServices");
        if (services == null) {
            return toolCallResult("listBookableServices", Map.of());
        }
        JsonNode match = bestMatchingService(fullUserText, services, false);
        if (match == null) {
            if (services.isEmpty()) {
                return textResult("We don't currently have any bookable services set up online — let me connect "
                        + "you with a team member who can help.");
            }
            return textResult("Which of these would you like to know about — " + serviceNameList(services) + "?");
        }
        return textResult(describeService(match));
    }

    // ------------------------------------------------------------------
    // GENERAL_INFO — hours/location questions go to the matching tool;
    // everything else is answered strictly from the knowledge base
    // supplied in the system prompt. Never invents an answer.
    // ------------------------------------------------------------------

    private AiProviderResult handleGeneralInfo(SystemPromptFacts facts, Map<String, JsonNode> toolResults, String fullUserText) {
        String t = fullUserText.toLowerCase(Locale.ROOT);

        if (containsAny(t, "open", "hour", "close", "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
                && !toolResults.containsKey("getBusinessHours") && bestKnowledgeMatch(facts, fullUserText) == null) {
            return toolCallResult("getBusinessHours", Map.of());
        }
        if (toolResults.containsKey("getBusinessHours")) {
            return textResult(describeHours(toolResults.get("getBusinessHours")));
        }

        if (containsAny(t, "located", "location", "where are you", "address", "contact", "phone number", "email address")
                && !toolResults.containsKey("getBusinessInfo") && bestKnowledgeMatch(facts, fullUserText) == null) {
            return toolCallResult("getBusinessInfo", Map.of());
        }
        if (toolResults.containsKey("getBusinessInfo")) {
            return textResult(describeBusinessInfo(toolResults.get("getBusinessInfo")));
        }

        KnowledgeEntry match = bestKnowledgeMatch(facts, fullUserText);
        if (match != null) {
            return textResult(match.content);
        }

        return textResult("I don't have information about that, I'm afraid — would you like me to connect you "
                + "with a team member who can help?");
    }

    // ------------------------------------------------------------------
    // BOOKING / AVAILABILITY — the multi-turn slot-filling flow.
    // ------------------------------------------------------------------

    private AiProviderResult handleBookingFlow(
            SystemPromptFacts facts, Map<String, JsonNode> toolResults, String fullUserText, String latest, boolean wantsToBook
    ) {
        // Step 1 — which service?
        JsonNode services = toolResults.get("listBookableServices");
        if (services == null) {
            return toolCallResult("listBookableServices", Map.of());
        }
        JsonNode service = bestMatchingService(fullUserText, services, true);
        if (service == null) {
            if (services.isEmpty()) {
                return textResult("We don't have any bookable services set up online yet — let me connect you "
                        + "with a team member.");
            }
            return textResult("Which service would you like — " + serviceNameList(services) + "?");
        }

        // Step 2 — when?
        Instant scheduledAt = resolveDateTime(fullUserText);
        if (scheduledAt == null) {
            return textResult("Happy to help! What date and time would you like to visit for the "
                    + service.path("serviceName").asText("service") + "?");
        }

        // Step 3 — is it actually available? (checked for both AVAILABILITY-only
        // questions and before ever booking — never books into an unavailable slot.)
        JsonNode availability = toolResults.get("checkAvailability");
        if (availability == null) {
            return toolCallResult("checkAvailability", Map.of(
                    "serviceId", serviceId(service),
                    "scheduledAt", scheduledAt.toString()
            ));
        }
        String when = formatDateTime(scheduledAt);
        if (!availability.path("available").asBoolean(false)) {
            String reason = availability.path("reason").asText("that time isn't available");
            return textResult("Unfortunately " + service.path("serviceName").asText("that")
                    + " isn't available on " + when + " — " + reason + ". Would you like to try another time?");
        }
        if (!wantsToBook) {
            return textResult("Good news — " + service.path("serviceName").asText("that") + " is available on "
                    + when + "! Would you like me to book it for you?");
        }

        // Step 4 — who's booking? (find-or-create by phone; never a duplicate.)
        String phone = extractPhone(fullUserText);
        if (phone == null) {
            return textResult("Great, let's get that booked! Could I get your name and best contact number to "
                    + "complete the booking?");
        }

        JsonNode customer = toolResults.get("findCustomer");
        if (customer == null) {
            return toolCallResult("findCustomer", Map.of("phone", phone));
        }
        if (!customer.path("found").isMissingNode() && !customer.path("found").asBoolean(true)
                && !toolResults.containsKey("createCustomer")) {
            String name = extractNameNearPhone(latest, fullUserText, phone);
            return toolCallResult("createCustomer", Map.of(
                    "fullName", name != null ? name : "Guest",
                    "phone", phone,
                    "email", placeholderEmail(phone)
            ));
        }
        JsonNode resolvedCustomer = toolResults.containsKey("createCustomer") ? toolResults.get("createCustomer") : customer;
        String fallbackName = extractNameNearPhone(latest, fullUserText, phone);
        String customerName = resolvedCustomer.path("fullName").asText(fallbackName);
        if (customerName == null || customerName.isBlank()) customerName = "Guest";

        // Step 5 — explicit confirmation before actually booking.
        if (!hasConfirmation(latest) && !toolResults.containsKey("createBooking")) {
            return textResult("Here's what I have: " + service.path("serviceName").asText("your visit") + " on "
                    + when + ", under " + customerName + " (" + phone + "). Shall I go ahead and confirm this booking?");
        }

        if (!toolResults.containsKey("createBooking")) {
            return toolCallResult("createBooking", Map.of(
                    "serviceId", serviceId(service),
                    "customerName", customerName,
                    "customerPhone", phone,
                    "customerEmail", placeholderEmail(phone),
                    "scheduledAt", scheduledAt.toString()
            ));
        }

        JsonNode booking = toolResults.get("createBooking");
        if (booking.has("error")) {
            return textResult("I couldn't complete that booking — " + booking.path("error").asText("something went wrong")
                    + ". Would you like to try a different time?");
        }
        return textResult("All set! Your " + service.path("serviceName").asText("booking") + " for " + when
                + " is confirmed (booking #" + booking.path("bookingNumber").asText("") + "). We look forward to "
                + "seeing you, " + customerName + "!");
    }

    // ------------------------------------------------------------------
    // Tool-result / conversation-history helpers
    // ------------------------------------------------------------------

    private String lastUserMessage(List<AiProviderMessage> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if ("user".equals(conversation.get(i).role())) return conversation.get(i).content();
        }
        return null;
    }

    private String allUserText(List<AiProviderMessage> conversation) {
        StringBuilder sb = new StringBuilder();
        for (AiProviderMessage m : conversation) {
            if ("user".equals(m.role())) sb.append(m.content()).append(' ');
        }
        return sb.toString();
    }

    // Every earlier customer turn, deliberately excluding the very latest
    // one — used by classify() to recognize "we're already mid-flow" from
    // what the customer said in prior turns, without the current turn's own
    // (possibly short/ambiguous) text drowning that signal out.
    private String allUserTextExceptLatest(List<AiProviderMessage> conversation) {
        int lastUserIdx = -1;
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if ("user".equals(conversation.get(i).role())) {
                lastUserIdx = i;
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lastUserIdx; i++) {
            AiProviderMessage m = conversation.get(i);
            if ("user".equals(m.role())) sb.append(m.content()).append(' ');
        }
        return sb.toString();
    }

    // Only tool results produced by THIS turn's own tool-call loop (i.e.
    // since the most recent user message) — a fresh HTTP turn never
    // replays prior turns' tool activity, matching AiChatService's own
    // history-building (only USER/ASSISTANT text is persisted/replayed).
    private Map<String, JsonNode> toolResultsSinceLastUserMessage(List<AiProviderMessage> conversation) {
        int lastUserIdx = -1;
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if ("user".equals(conversation.get(i).role())) {
                lastUserIdx = i;
                break;
            }
        }
        Map<String, String> idToName = new HashMap<>();
        Map<String, JsonNode> results = new LinkedHashMap<>();
        for (int i = lastUserIdx + 1; i < conversation.size(); i++) {
            AiProviderMessage m = conversation.get(i);
            if ("assistant".equals(m.role()) && m.toolCalls() != null) {
                for (AiToolCall c : m.toolCalls()) idToName.put(c.id(), c.name());
            } else if ("tool".equals(m.role()) && m.toolCallId() != null) {
                String name = idToName.get(m.toolCallId());
                if (name != null) {
                    try {
                        results.put(name, objectMapper.readTree(m.content()));
                    } catch (Exception ignored) {
                        // malformed tool result — treat as absent rather than crash the mock
                    }
                }
            }
        }
        return results;
    }

    // ------------------------------------------------------------------
    // System-prompt parsing — the mock's ONLY source of business-specific
    // truth, exactly like a real model reading the same prompt. Never
    // hardcodes any business's name/services/knowledge.
    // ------------------------------------------------------------------

    private record KnowledgeEntry(String category, String title, String content) {
    }

    private record SystemPromptFacts(List<KnowledgeEntry> knowledge, String humanHandoffMessage) {
    }

    private static final Pattern KNOWLEDGE_LINE = Pattern.compile("^-\\s*\\[(.+?)]\\s*(.+?):\\s*(.*)$");
    private static final Pattern HANDOFF_LINE = Pattern.compile("use this message after escalating:\\s*\"(.*?)\"");

    private SystemPromptFacts parseSystemPrompt(String systemPrompt) {
        List<KnowledgeEntry> knowledge = new ArrayList<>();
        if (systemPrompt != null) {
            for (String line : systemPrompt.split("\n")) {
                Matcher m = KNOWLEDGE_LINE.matcher(line.trim());
                if (m.matches()) {
                    knowledge.add(new KnowledgeEntry(m.group(1), m.group(2), m.group(3)));
                }
            }
        }
        String handoff = null;
        if (systemPrompt != null) {
            Matcher m = HANDOFF_LINE.matcher(systemPrompt);
            if (m.find()) handoff = m.group(1);
        }
        return new SystemPromptFacts(knowledge, handoff);
    }

    // Simple word-overlap scoring against title+content — good enough for a
    // small demo knowledge base; this is exactly the kind of thing a real
    // vector/semantic search would replace later (explicitly out of scope
    // for Phase 1/2 per the architecture).
    private KnowledgeEntry bestKnowledgeMatch(SystemPromptFacts facts, String text) {
        List<String> queryWords = List.of(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"));
        KnowledgeEntry best = null;
        int bestScore = 0;
        for (KnowledgeEntry entry : facts.knowledge) {
            int score = 0;
            for (String kw : (entry.title + " " + entry.content).toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (kw.length() <= 3) continue;
                for (String qw : queryWords) {
                    if (qw.length() <= 3) continue;
                    // Word-stem match in either direction ("open" <-> "opening",
                    // "hour" <-> "hours") rather than requiring the query to
                    // literally contain the whole knowledge word verbatim.
                    if (kw.startsWith(qw) || qw.startsWith(kw)) {
                        score++;
                        break;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = entry;
            }
        }
        return bestScore > 0 ? best : null;
    }

    // ------------------------------------------------------------------
    // Tool-result formatting helpers
    // ------------------------------------------------------------------

    private JsonNode bestMatchingService(String text, JsonNode services, boolean defaultToOnlyOption) {
        String t = text.toLowerCase(Locale.ROOT);
        JsonNode best = null;
        int bestScore = 0;
        for (JsonNode svc : services) {
            String name = svc.path("serviceName").asText("").toLowerCase(Locale.ROOT);
            int score = 0;
            for (String word : name.split("\\s+")) {
                if (word.length() > 2 && t.contains(word)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = svc;
            }
        }
        if (best != null) return best;
        if (defaultToOnlyOption && services.size() == 1) return services.get(0);
        return null;
    }

    private String serviceNameList(JsonNode services) {
        List<String> names = new ArrayList<>();
        for (JsonNode svc : services) names.add(svc.path("serviceName").asText("a service"));
        return String.join(", ", names);
    }

    private String describeService(JsonNode service) {
        String name = service.path("serviceName").asText("This service");
        String price = service.path("price").asText("0");
        String description = service.path("description").asText(null);
        StringBuilder sb = new StringBuilder(name).append(" is GH₵").append(price).append('.');
        if (description != null && !description.isBlank()) sb.append(' ').append(description);
        return sb.toString();
    }

    private String describeHours(JsonNode hours) {
        if (!hours.isArray() || hours.isEmpty()) {
            return "I don't have our working hours on file right now — let me connect you with a team member.";
        }
        StringBuilder sb = new StringBuilder("Our hours are: ");
        List<String> parts = new ArrayList<>();
        for (JsonNode h : hours) {
            parts.add(dayName(h.path("dayOfWeek").asInt()) + " " + h.path("startTime").asText("") + "–" + h.path("endTime").asText(""));
        }
        return sb.append(String.join(", ", parts)).toString();
    }

    private String describeBusinessInfo(JsonNode info) {
        StringBuilder sb = new StringBuilder();
        String location = info.path("location").asText(null);
        String phone = info.path("contactPhone").asText(null);
        String email = info.path("contactEmail").asText(null);
        if (location != null && !location.isBlank()) sb.append("We're located at ").append(location).append(". ");
        if (phone != null && !phone.isBlank()) sb.append("You can reach us on ").append(phone).append(". ");
        if (email != null && !email.isBlank()) sb.append("Or email ").append(email).append('.');
        if (sb.isEmpty()) sb.append("I don't have that on file — let me connect you with a team member.");
        return sb.toString().trim();
    }

    private String dayName(int isoWeekday) {
        return switch (isoWeekday) {
            case 1 -> "Mon";
            case 2 -> "Tue";
            case 3 -> "Wed";
            case 4 -> "Thu";
            case 5 -> "Fri";
            case 6 -> "Sat";
            case 7 -> "Sun";
            default -> "";
        };
    }

    // ------------------------------------------------------------------
    // Slot extraction from free text
    // ------------------------------------------------------------------

    private static final Map<String, DayOfWeek> WEEKDAYS = Map.ofEntries(
            Map.entry("monday", DayOfWeek.MONDAY), Map.entry("tuesday", DayOfWeek.TUESDAY),
            Map.entry("wednesday", DayOfWeek.WEDNESDAY), Map.entry("thursday", DayOfWeek.THURSDAY),
            Map.entry("friday", DayOfWeek.FRIDAY), Map.entry("saturday", DayOfWeek.SATURDAY),
            Map.entry("sunday", DayOfWeek.SUNDAY)
    );

    // Only matches a time when it's unambiguous ("at 2", "2pm", "14:00") —
    // deliberately does NOT match a bare number, so "6 of us" is never
    // misread as "6 o'clock".
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "\\bat\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b|\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:\\+?233|0)[\\d][\\d\\s-]{7,11}\\d");

    private Instant resolveDateTime(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        DayOfWeek target = null;
        for (Map.Entry<String, DayOfWeek> e : WEEKDAYS.entrySet()) {
            if (t.contains(e.getKey())) {
                target = e.getValue();
                break;
            }
        }
        boolean tomorrow = t.contains("tomorrow");
        boolean today = t.contains("today");
        if (target == null && !tomorrow && !today) return null;

        ZonedDateTime base = ZonedDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime date = tomorrow ? base.plusDays(1)
                : today ? base
                : base.with(TemporalAdjusters.nextOrSame(target));

        int hour = 14;
        int minute = 0;
        Matcher m = TIME_PATTERN.matcher(t);
        if (m.find()) {
            String hourStr = m.group(1) != null ? m.group(1) : m.group(4);
            String minuteStr = m.group(2) != null ? m.group(2) : m.group(5);
            String ampm = m.group(3) != null ? m.group(3) : m.group(6);
            int h = Integer.parseInt(hourStr);
            if ("pm".equalsIgnoreCase(ampm) && h < 12) h += 12;
            if ("am".equalsIgnoreCase(ampm) && h == 12) h = 0;
            hour = h;
            minute = minuteStr != null ? Integer.parseInt(minuteStr) : 0;
        }
        return date.withHour(hour).withMinute(minute).toInstant();
    }

    private String extractPhone(String text) {
        Matcher m = PHONE_PATTERN.matcher(text);
        while (m.find()) {
            String candidate = m.group();
            if (PhoneUtils.isValid(candidate)) return candidate;
        }
        return null;
    }

    // Prefers the LATEST message — a customer typically states their name in
    // direct response to being asked, so that single turn isolates it far
    // more cleanly than the whole accumulated conversation would (which picks
    // up every other word said across every prior turn too). Falls back to
    // the full text only if the latest turn alone doesn't yield anything,
    // covering the rarer case of a name and phone given in separate turns.
    private String extractNameNearPhone(String latest, String fullUserText, String phone) {
        String fromLatest = extractName(latest, phone);
        return fromLatest != null ? fromLatest : extractName(fullUserText, phone);
    }

    private String extractName(String text, String phone) {
        String cleaned = phone != null ? text.replace(phone, " ") : text;
        cleaned = cleaned
                .replaceAll("(?i)\\bi'?m\\b", " ")
                .replaceAll("(?i)\\bmy name is\\b", " ")
                .replaceAll("(?i)\\bthis is\\b", " ")
                .replaceAll("(?i)\\bcall me\\b", " ")
                .replaceAll("(?i)\\bit'?s\\b", " ")
                .replaceAll("[^A-Za-z '-]", " ")
                .trim()
                .replaceAll("\\s+", " ");
        if (cleaned.isBlank() || cleaned.length() > 60) return null;
        String[] words = cleaned.split(" ");
        StringBuilder name = new StringBuilder();
        for (String w : words) {
            if (w.isBlank()) continue;
            name.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT)).append(' ');
        }
        String result = name.toString().trim();
        return result.isBlank() ? null : result;
    }

    private boolean hasConfirmation(String latest) {
        String t = latest.toLowerCase(Locale.ROOT);
        return containsAny(t, "yes", "confirm", "go ahead", "book it", "please book", "sounds good", "sure");
    }

    // Never resort-specific — a plain, generic placeholder domain used only
    // when a guest doesn't supply an email, so createBooking's existing
    // @Email/@NotBlank validation (reused as-is, never relaxed) is satisfied
    // without adding an extra conversational turn just for the demo.
    private String placeholderEmail(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return "guest" + digits + "@guest.local";
    }

    private String serviceId(JsonNode service) {
        JsonNode catalogId = service.path("serviceCatalogId");
        if (!catalogId.isMissingNode() && !catalogId.isNull()) return catalogId.asText();
        return service.path("packageId").asText();
    }

    private String formatDateTime(Instant instant) {
        return DateTimeFormatter.ofPattern("EEEE d MMM 'at' h:mm a", Locale.ENGLISH)
                .withZone(ZoneOffset.UTC)
                .format(instant);
    }

    // Presence check only (unlike resolveDateTime, which actually computes
    // an Instant) — used purely to help classify() recognize a date/time
    // reference in phrasing that doesn't use an explicit booking/
    // availability word at all.
    private boolean mentionsDateOrTime(String lowerText) {
        if (lowerText.contains("tomorrow") || lowerText.contains("today")) return true;
        for (String day : WEEKDAYS.keySet()) {
            if (lowerText.contains(day)) return true;
        }
        return TIME_PATTERN.matcher(lowerText).find();
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }

    private AiProviderResult textResult(String content) {
        return new AiProviderResult(content, List.of());
    }

    private AiProviderResult toolCallResult(String toolName, Map<String, Object> arguments) {
        try {
            String argsJson = objectMapper.writeValueAsString(arguments);
            return new AiProviderResult(null, List.of(new AiToolCall("mock-" + toolName, toolName, argsJson)));
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't serialize mock tool arguments", e);
        }
    }
}
