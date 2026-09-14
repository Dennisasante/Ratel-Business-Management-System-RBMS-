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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    private enum Intent { HUMAN_HANDOFF, PACKAGE_BOOKING, EVENT_LEAD, BOOKING, AVAILABILITY, SERVICE_INFO, GENERAL_INFO }

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
            case PACKAGE_BOOKING -> handlePackageBooking(facts, toolResults, fullUserText, latest, conversation);
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
        // Checked BEFORE the generic event-lead trigger below: a dinner/package reservation is a
        // real, fully-transactable flow (Restaurant-AI-demo phase) and must win over the
        // lead-capture-only path even though phrasing like "organise dinner for 8 people" would
        // otherwise also match "organi"/"event for" below. handlePackageBooking() itself falls
        // back to the lead-capture behaviour if this business has no real bookable packages, so
        // this never regresses a business (like Paradise Beach Resort) that has none.
        if (looksLikePackageBooking(t, priorUserText)) {
            return Intent.PACKAGE_BOOKING;
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

        if (containsAny(t, "how much", "cost", "price", "tell me about", "cabana", "day pass")
                || mentionsPackageGenuinely(t)) {
            return Intent.SERVICE_INFO;
        }
        return Intent.GENERAL_INFO;
    }

    private static final Pattern PARTY_SIZE_PATTERN = Pattern.compile(
            "\\b(\\d{1,3})\\s*(?:people|guests|persons|pax)\\b", Pattern.CASE_INSENSITIVE);

    // Real bug found via live browser testing: "I don't want a package. What sandwiches do you
    // have?" still contains the bare substring "package", so a naive containsAny check routed it
    // into the package flow anyway — the exact "do not accidentally force the package flow"
    // failure this whole regular-menu requirement exists to prevent. Suppresses the match when
    // "package(s)" is itself negated right there in the same message.
    private static final Pattern PACKAGE_NEGATION = Pattern.compile(
            "(?:don'?t want|do not want|not|without|skip|no)\\s+(?:a\\s+|the\\s+)?packages?\\b",
            Pattern.CASE_INSENSITIVE);

    private boolean mentionsPackageGenuinely(String t) {
        return (t.contains("package") || t.contains("packages")) && !PACKAGE_NEGATION.matcher(t).find();
    }

    // Deliberately checked ahead of "book"/"reserve" alone matching plain BOOKING — a package
    // reservation is still a superset of an ordinary booking request, so handlePackageBooking()
    // itself falls back to the exact same plain-booking flow (never lead-capture) whenever this
    // business turns out to have no real bookable packages, meaning this trigger firing "too
    // eagerly" is always safe, never a regression for a business like Paradise Beach Resort.
    private boolean looksLikePackageBooking(String t, String priorUserText) {
        boolean trigger = mentionsPackageGenuinely(t)
                || containsAny(t, "dinner for", "table for", "dinner package")
                || (PARTY_SIZE_PATTERN.matcher(t).find()
                        && containsAny(t, "dinner", "reservation", "book", "organi", "event", "party"));
        if (trigger) return true;
        String priorLower = priorUserText == null ? "" : priorUserText.toLowerCase(Locale.ROOT);
        return mentionsPackageGenuinely(priorLower) || containsAny(priorLower, "dinner for", "table for");
    }

    // Last (rightmost) match wins across the WHOLE accumulated conversation, so a later "make it
    // 12" correctly overrides an earlier "8 people" rather than the two being ambiguous.
    private Integer extractPartySize(String text) {
        Matcher m = PARTY_SIZE_PATTERN.matcher(text);
        Integer last = null;
        while (m.find()) {
            last = Integer.parseInt(m.group(1));
        }
        return last;
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
    // PACKAGE_BOOKING — Restaurant-AI-demo phase. A deterministic slot-filling state machine
    // standing in for a real LLM's own reasoning over the package-customization/policy-
    // acknowledgement tools (getPackageOptions/previewPackagePricing/getApplicablePolicies/
    // acknowledgePolicy) — exactly the same "call the real tool, read its real result, decide the
    // next step" discipline as handleBookingFlow() below, just with more steps. Like every other
    // handler in this file, it never touches a repository/service directly and never invents a
    // business's packages/prices/policies — everything comes from real tool results or the real
    // knowledge dump in the system prompt.
    //
    // Cross-turn memory constraint: AiChatService never replays a prior turn's tool CALLS/RESULTS
    // back to the provider (see buildProviderHistory's own "TOOL-role rows are provider-loop-
    // internal, not replayed as history") — only prior USER/ASSISTANT plain text survives across
    // turns. So, exactly like handleBookingFlow(), every slot (package, party size, substitutions,
    // date/time, phone) is re-derived from the FULL accumulated customer text on every single
    // turn, never cached. The one exception is knowing "was the customer just asked to confirm"
    // (needed to distinguish the package-confirmation step from an earlier "yes"): rather than
    // grepping the assistant's own PRIOR reply for incidental wording (the class-level comment on
    // classify() explains why that's fragile for INTENT classification), this uses one fixed,
    // self-owned protocol marker sentence this class itself always emits verbatim at that exact
    // step — not an inferred pattern, a deliberate contract between this method and itself.
    //
    // Batching: MAX_TOOL_ITERATIONS (AiChatService) caps how many times the "model" is consulted
    // per turn, not how many tools it may request per consultation. Several genuinely independent
    // tool calls (e.g. checkAvailability + findCustomer; previewPackagePricing +
    // getApplicablePolicies + beginPolicyCommitment; acknowledgePolicy + createBooking) are
    // therefore requested together in one AiProviderResult whenever their own inputs are already
    // known, keeping even a single "say everything in one message" turn well inside the cap.
    // ------------------------------------------------------------------

    private AiProviderResult handlePackageBooking(SystemPromptFacts facts, Map<String, JsonNode> toolResults,
                                                    String fullUserText, String latest, List<AiProviderMessage> conversation) {
        JsonNode services = toolResults.get("listBookableServices");
        if (services == null) {
            return toolCallResult("listBookableServices", Map.of());
        }
        List<JsonNode> packages = new ArrayList<>();
        services.forEach(s -> {
            if (s.path("isPackage").asBoolean(false)) packages.add(s);
        });
        if (packages.isEmpty()) {
            // This business has no real canonical packages — never fabricate a package flow;
            // fall back to the exact same plain-service booking flow any other request would use.
            return handleBookingFlow(facts, toolResults, fullUserText, latest, true);
        }

        JsonNode chosenPackage = matchPackageByName(fullUserText, packages);
        if (chosenPackage == null) {
            StringBuilder sb = new StringBuilder("We have ").append(packages.size()).append(" dinner packages available:\n");
            for (JsonNode p : packages) {
                sb.append("- ").append(p.path("serviceName").asText()).append(" — GH₵").append(money(p.path("price"))).append(" per guest\n");
            }
            sb.append("Which would you like?");
            return textResult(sb.toString());
        }
        String packageId = chosenPackage.path("packageId").asText();

        JsonNode options = toolResults.get("getPackageOptions");
        if (options == null) {
            return toolCallResult("getPackageOptions", Map.of("packageId", packageId));
        }

        String invalidSubstitutionReply = checkForInvalidSubstitution(facts, options, latest);
        if (invalidSubstitutionReply != null) {
            return textResult(invalidSubstitutionReply);
        }

        Map<String, String> selections = resolveSelections(options, fullUserText);
        Integer partySize = extractPartySize(fullUserText);
        Instant scheduledAt = resolveDateTime(fullUserText);
        String phone = extractPhone(fullUserText);

        // ---- Batch: availability + customer lookup, whichever are independently fetchable now ----
        List<AiToolCall> batchA = new ArrayList<>();
        JsonNode availability = toolResults.get("checkAvailability");
        if (availability == null && scheduledAt != null) {
            batchA.add(new AiToolCall("mock-checkAvailability", "checkAvailability",
                    toJson(Map.of("serviceId", packageId, "scheduledAt", scheduledAt.toString()))));
        }
        JsonNode foundCustomer = toolResults.get("findCustomer");
        if (foundCustomer == null && phone != null) {
            batchA.add(new AiToolCall("mock-findCustomer", "findCustomer", toJson(Map.of("phone", phone))));
        }
        if (!batchA.isEmpty()) {
            return new AiProviderResult(null, batchA);
        }

        if (partySize == null) {
            return textResult("Great choice — the " + chosenPackage.path("serviceName").asText() + "! For how many guests?");
        }
        if (scheduledAt == null) {
            return textResult("What date and time would you like this reservation for?");
        }
        if (availability == null) {
            return toolCallResult("checkAvailability", Map.of("serviceId", packageId, "scheduledAt", scheduledAt.toString()));
        }
        String when = formatDateTime(scheduledAt);
        if (!availability.path("available").asBoolean(false)) {
            String reason = availability.path("reason").asText("that time isn't available");
            return textResult("That time isn't available — " + reason + ". Would you like to try another time?");
        }
        if (phone == null) {
            return textResult("Great, let's lock in the details — could I get your name and best contact number?");
        }
        if (foundCustomer == null) {
            return toolCallResult("findCustomer", Map.of("phone", phone));
        }
        JsonNode resolvedCustomer;
        if (!foundCustomer.path("found").asBoolean(true)) {
            JsonNode created = toolResults.get("createCustomer");
            if (created == null) {
                String name = extractNameNearPhone(latest, fullUserText, phone);
                return toolCallResult("createCustomer", Map.of(
                        "fullName", name != null ? name : "Guest", "phone", phone, "email", placeholderEmail(phone)));
            }
            resolvedCustomer = created;
        } else {
            resolvedCustomer = foundCustomer;
        }
        String fallbackName = extractNameNearPhone(latest, fullUserText, phone);
        String customerName = resolvedCustomer.path("fullName").asText(fallbackName);
        if (customerName == null || customerName.isBlank()) customerName = "Guest";

        // ---- Batch: pricing + policy content + a fresh commitment — mutually independent ----
        List<AiToolCall> batchB = new ArrayList<>();
        JsonNode pricing = toolResults.get("previewPackagePricing");
        if (pricing == null) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("packageId", packageId);
            args.put("selections", selections);
            args.put("partySize", partySize);
            batchB.add(new AiToolCall("mock-previewPackagePricing", "previewPackagePricing", toJson(args)));
        }
        JsonNode policies = toolResults.get("getApplicablePolicies");
        if (policies == null) {
            batchB.add(new AiToolCall("mock-getApplicablePolicies", "getApplicablePolicies", "{}"));
        }
        JsonNode commitment = toolResults.get("beginPolicyCommitment");
        if (commitment == null) {
            batchB.add(new AiToolCall("mock-beginPolicyCommitment", "beginPolicyCommitment", "{}"));
        }
        if (!batchB.isEmpty()) {
            return new AiProviderResult(null, batchB);
        }

        String confirmMarker = "Shall I go ahead and confirm this reservation?";
        String lastAssistant = lastAssistantText(conversation);
        boolean awaitingConfirmation = lastAssistant != null && lastAssistant.contains(confirmMarker);

        if (!awaitingConfirmation || !hasConfirmation(latest)) {
            return textResult(buildOrderAndPolicySummary(chosenPackage, options, selections, partySize, pricing, facts, when, confirmMarker));
        }

        // ---- Customer confirmed — and, by confirming right after the policy excerpt shown in
        // the same message, explicitly acknowledged it. Record the REAL disclosure/acknowledgement
        // via PolicyEngine before ever attempting the booking; never a fabricated "accepted=true". ----
        if (policies.isEmpty()) {
            JsonNode booking = toolResults.get("createBooking");
            if (booking == null) {
                return toolCallResult("createBooking", bookingArgs(packageId, customerName, phone, scheduledAt, selections, partySize, null));
            }
            return finalBookingText(booking, partySize);
        }

        UUID commitmentReference = UUID.fromString(commitment.path("commitmentReference").asText());
        List<AiToolCall> batchC = new ArrayList<>();
        if (!toolResults.containsKey("acknowledgePolicy")) {
            String policyId = policies.get(0).path("policyId").asText();
            batchC.add(new AiToolCall("mock-acknowledgePolicy", "acknowledgePolicy", toJson(Map.of(
                    "policyId", policyId, "commitmentReference", commitmentReference.toString()))));
        }
        if (!toolResults.containsKey("createBooking")) {
            batchC.add(new AiToolCall("mock-createBooking", "createBooking",
                    toJson(bookingArgs(packageId, customerName, phone, scheduledAt, selections, partySize, commitmentReference))));
        }
        if (!batchC.isEmpty()) {
            return new AiProviderResult(null, batchC);
        }

        return finalBookingText(toolResults.get("createBooking"), partySize);
    }

    private Map<String, Object> bookingArgs(String packageId, String customerName, String phone, Instant scheduledAt,
                                             Map<String, String> selections, int partySize, UUID commitmentReference) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("serviceId", packageId);
        args.put("customerName", customerName);
        args.put("customerPhone", phone);
        args.put("customerEmail", placeholderEmail(phone));
        args.put("scheduledAt", scheduledAt.toString());
        args.put("selections", selections);
        args.put("partySize", partySize);
        if (commitmentReference != null) args.put("commitmentReference", commitmentReference.toString());
        return args;
    }

    private AiProviderResult finalBookingText(JsonNode booking, int partySize) {
        if (booking == null || booking.has("error")) {
            String reason = booking != null ? booking.path("error").asText("something went wrong") : "something went wrong";
            return textResult("I couldn't complete that reservation — " + reason + ". Would you like to try again?");
        }
        return textResult("This is a DEMO — no real payment was processed, but your deposit has been simulated and "
                + "recorded as received. Your reservation for " + partySize + " guests is confirmed! Booking #"
                + booking.path("bookingNumber").asText("") + ".");
    }

    // componentId(String) -> optionId(String) — for whichever component the customer's LATEST
    // GENUINE mention (default label OR an alternative's label, whichever appears later in the
    // full accumulated text) names something other than the default. A component never mentioned
    // at all is simply omitted, exactly matching PackagePricingService's own "omission implies
    // default" contract — never re-implemented here, just fed through it.
    private Map<String, String> resolveSelections(JsonNode options, String fullUserText) {
        Map<String, String> selections = new LinkedHashMap<>();
        String lower = fullUserText.toLowerCase(Locale.ROOT);
        for (JsonNode component : options.path("components")) {
            String componentId = component.path("componentId").asText();
            String defaultLabel = component.path("defaultLabel").asText(null);
            int bestIndex = -1;
            String bestOptionId = null;
            if (defaultLabel != null) {
                int idx = lastGenuineIndexOf(lower, defaultLabel);
                if (idx > bestIndex) {
                    bestIndex = idx;
                    bestOptionId = null; // explicit re-mention of the default — stays default, no entry needed
                }
            }
            for (JsonNode alt : component.path("alternatives")) {
                String label = alt.path("label").asText();
                int idx = lastGenuineIndexOf(lower, label);
                if (idx > bestIndex) {
                    bestIndex = idx;
                    bestOptionId = alt.path("optionId").asText();
                }
            }
            if (bestOptionId != null) {
                selections.put(componentId, bestOptionId);
            }
        }
        return selections;
    }

    // A label's textual position alone isn't enough to tell "the item being requested" from "the
    // item being replaced" — in "Snapper instead of the seafood espetada", the DEFAULT label
    // ("seafood espetada") appears LATER in the sentence than the alternative actually being
    // asked for, so a naive last-mention-wins reading would (and, caught during testing, did)
    // pick the wrong one. This skips any mention of `label` that's immediately preceded by a
    // negating reference phrase ("instead of", "rather than", "not the", "no more", "skip the",
    // each with an optional "the/a/an" article) and keeps searching earlier in the text for a
    // genuine one, so that specific grammatical pattern resolves correctly while everything else
    // (a bare mention, or a later change-of-mind explicitly re-naming an item) still uses the
    // same real last-mention-wins logic.
    private static final Pattern NEGATION_PREFIX = Pattern.compile(
            "(?:instead of|rather than|not the|no more|skip the)\\s+(?:the\\s+|a\\s+|an\\s+)?$");

    private int lastGenuineIndexOf(String lower, String label) {
        String needle = label.toLowerCase(Locale.ROOT);
        int searchFrom = lower.length();
        while (searchFrom > 0) {
            int idx = lower.lastIndexOf(needle, searchFrom - 1);
            if (idx < 0) return -1;
            if (!NEGATION_PREFIX.matcher(lower.substring(0, idx)).find()) {
                return idx;
            }
            searchFrom = idx;
        }
        return -1;
    }

    private static final Pattern SUBSTITUTION_TRIGGER = Pattern.compile(
            "instead of|swap|replace|can i (?:get|have)|could i (?:get|have)", Pattern.CASE_INSENSITIVE);

    // Rejects a substitution request naming a REAL menu item (drawn from the same knowledge dump
    // GENERAL_INFO/SERVICE_INFO already use — never an invented catalogue) that isn't actually a
    // valid default/alternative for ANY component of the CURRENTLY selected package, instead of
    // silently ignoring it or guessing. Never flags a request for something that genuinely IS
    // valid here — resolveSelections() picks those up on its own independent pass.
    private String checkForInvalidSubstitution(SystemPromptFacts facts, JsonNode options, String latest) {
        if (!SUBSTITUTION_TRIGGER.matcher(latest.toLowerCase(Locale.ROOT)).find()) {
            return null;
        }
        Set<String> validLabels = new HashSet<>();
        List<String> componentSummaries = new ArrayList<>();
        for (JsonNode component : options.path("components")) {
            List<String> names = new ArrayList<>();
            String defaultLabel = component.path("defaultLabel").asText(null);
            if (defaultLabel != null) {
                validLabels.add(defaultLabel.toLowerCase(Locale.ROOT));
                names.add(defaultLabel + " (default)");
            }
            for (JsonNode alt : component.path("alternatives")) {
                String label = alt.path("label").asText();
                validLabels.add(label.toLowerCase(Locale.ROOT));
                names.add(label);
            }
            componentSummaries.add(component.path("slotName").asText() + ": " + String.join(", ", names));
        }

        String latestLower = latest.toLowerCase(Locale.ROOT);
        for (String item : allKnownMenuItemNames(facts)) {
            if (validLabels.contains(item)) continue; // genuinely valid here — not an error
            if (latestLower.contains(item)) {
                return "I'm afraid \"" + titleCase(item) + "\" isn't available as a substitution in this package. "
                        + "Here's what you can actually choose from:\n" + String.join("\n", componentSummaries);
            }
        }
        return null;
    }

    // Parses every "Name — GH₵Price[ — Description]" line out of the MENU-category knowledge
    // entries (the exact format seedRegularMenuKnowledge/AiKnowledgeService feed into the system
    // prompt) — the full real catalogue, never a hardcoded restaurant-specific list.
    private static final Pattern MENU_ITEM_LINE = Pattern.compile("^(.+?)\\s*—\\s*GH₵");

    private Set<String> allKnownMenuItemNames(SystemPromptFacts facts) {
        Set<String> names = new HashSet<>();
        for (KnowledgeEntry entry : facts.knowledge) {
            if (!"MENU".equals(entry.category())) continue;
            for (String line : entry.content().split("\n")) {
                Matcher m = MENU_ITEM_LINE.matcher(line.trim());
                if (m.find()) {
                    names.add(m.group(1).trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return names;
    }

    // Real bug found via live browser testing: "What dinner packages do you have?" (a generic
    // question naming no specific package) was matched to "Classic Dinner" anyway, because that
    // was the only package whose own name happens to contain the word "dinner" — a word the
    // GENERIC QUESTION about packages was always going to contain too. Excluded from partial-word
    // scoring below so a shared generic word can never masquerade as a genuine package mention;
    // a real customer naming even part of a package's own distinctive name ("the Classic", "the
    // Seafood one") still matches correctly.
    private static final Set<String> GENERIC_PACKAGE_WORDS = Set.of("dinner", "package", "packages", "menu", "guest", "guests");

    private JsonNode matchPackageByName(String fullUserText, List<JsonNode> packages) {
        String lower = fullUserText.toLowerCase(Locale.ROOT);
        for (JsonNode p : packages) {
            String name = p.path("serviceName").asText("").toLowerCase(Locale.ROOT);
            if (!name.isBlank() && lower.contains(name)) return p; // full name mentioned — unambiguous, wins outright
        }
        JsonNode best = null;
        int bestScore = 0;
        for (JsonNode p : packages) {
            String name = p.path("serviceName").asText("").toLowerCase(Locale.ROOT);
            int score = 0;
            for (String word : name.split("\\s+")) {
                if (word.length() > 3 && !GENERIC_PACKAGE_WORDS.contains(word) && lower.contains(word)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private String buildOrderAndPolicySummary(JsonNode chosenPackage, JsonNode options, Map<String, String> selections,
                                               int partySize, JsonNode pricing, SystemPromptFacts facts, String when,
                                               String confirmMarker) {
        StringBuilder sb = new StringBuilder();
        sb.append("Here's your ").append(chosenPackage.path("serviceName").asText())
                .append(" for ").append(partySize).append(" guests on ").append(when).append(":\n");
        for (JsonNode component : options.path("components")) {
            String componentId = component.path("componentId").asText();
            String label = selections.containsKey(componentId)
                    ? labelForOption(component, selections.get(componentId))
                    : component.path("defaultLabel").asText();
            sb.append("- ").append(component.path("slotName").asText()).append(": ").append(label).append('\n');
        }
        sb.append("Per guest: GH₵").append(money(pricing.path("perGuestPrice")))
                .append(" — Total: GH₵").append(money(pricing.path("totalPrice"))).append('\n');
        sb.append("A 70% deposit of GH₵").append(money(pricing.path("depositAmount")))
                .append(" secures the booking, with the remaining GH₵").append(money(pricing.path("balanceAmount")))
                .append(" due before the event date.\n");

        // Only the topics actually relevant to confirming THIS reservation — never the whole
        // policy document — pulled from the real POLICY knowledge entries, never hardcoded text.
        StringBuilder relevant = new StringBuilder();
        for (KnowledgeEntry k : facts.knowledge) {
            if (!"POLICY".equals(k.category())) continue;
            String titleLower = k.title().toLowerCase(Locale.ROOT);
            if (titleLower.contains("deposit") || titleLower.contains("confirming")) {
                relevant.append(k.content()).append(' ');
            }
        }
        if (!relevant.isEmpty()) {
            sb.append("A couple of important policies: ").append(relevant.toString().trim()).append('\n');
        }
        sb.append(confirmMarker);
        return sb.toString();
    }

    private String labelForOption(JsonNode component, String optionId) {
        for (JsonNode alt : component.path("alternatives")) {
            if (optionId.equals(alt.path("optionId").asText())) return alt.path("label").asText();
        }
        return component.path("defaultLabel").asText();
    }

    private String titleCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.split(" ")) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    // Deliberately reads the ASSISTANT's own immediately preceding plain-text turn — unlike
    // classify()'s explicit avoidance of that pattern for INTENT detection (see this class's own
    // header comment), this checks for one fixed, self-authored protocol marker sentence this
    // very method always emits verbatim at exactly one step, not an inferred/incidental phrase.
    // It is the only way to disambiguate "this affirmative reply is answering the package/policy
    // confirmation question" from any other "yes" earlier in the conversation, given tool results
    // never persist across turns (see this class's own note above).
    private String lastAssistantText(List<AiProviderMessage> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            AiProviderMessage m = conversation.get(i);
            if ("assistant".equals(m.role()) && m.content() != null && !m.content().isBlank()) {
                return m.content();
            }
        }
        return null;
    }

    // A JsonNode round-tripped through Jackson's default numeric handling doesn't reliably
    // preserve a BigDecimal's original scale as text (observed: a real GH₵768.00 coming back as
    // "768.0") — never acceptable for a displayed currency amount. Always re-normalizes to
    // exactly 2 decimal places from the real numeric value, never reformats/guesses beyond that.
    private String money(JsonNode node) {
        try {
            return new java.math.BigDecimal(node.asText()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        } catch (Exception e) {
            return node.asText();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't serialize mock tool arguments", e);
        }
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

        // Real bug found via live browser testing: "Do you have a DJ or live music this
        // Saturday?" was misread as an hours question (bare day-of-week trigger) and answered
        // with business hours instead of honestly admitting the DJ/live-music info isn't known —
        // exactly the hallucination this whole unknown-information behaviour exists to prevent.
        // A weekday name alone is no longer enough; an actual hours-shaped word must be present
        // too (still matches "are you open on Saturday", never a bare day mention on its own).
        if (containsAny(t, "open", "hour", "close", "opening", "closing")
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

        // "What's your cheapest side?" — compute the real minimum from the matched category's own
        // real price lines rather than dumping the whole category and leaving the customer to
        // find it themselves.
        if (containsAny(t, "cheapest", "lowest price", "least expensive")) {
            KnowledgeEntry category = bestKnowledgeMatch(facts, fullUserText);
            if (category != null && "MENU".equals(category.category())) {
                String cheapest = cheapestItemIn(category.content());
                if (cheapest != null) return textResult(cheapest);
            }
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
            // Restaurant-AI-demo phase — real bug found while adding package-flow support: a
            // knowledge entry whose own CONTENT spans multiple physical lines (e.g. one seeded
            // MENU category's full item list) was silently truncated to just its first line,
            // because AiChatService.buildSystemPrompt prints "- [cat] title: content" as one
            // logical entry but content itself may contain embedded '\n's, and split("\n") then
            // breaks it apart before the old per-line regex ever saw the rest. Fixed generically
            // here (not restaurant-specific) by scanning forward from each "- [...]" line and
            // folding in every following line until the NEXT "- [...]" entry or a blank line —
            // this also fixes GENERAL_INFO/SERVICE_INFO's own pre-existing knowledge lookup for
            // any multi-line entry, not just MENU ones.
            String[] lines = systemPrompt.split("\n", -1);
            int i = 0;
            while (i < lines.length) {
                Matcher m = KNOWLEDGE_LINE.matcher(lines[i].trim());
                if (m.matches()) {
                    String category = m.group(1);
                    String title = m.group(2);
                    StringBuilder content = new StringBuilder(m.group(3));
                    int j = i + 1;
                    while (j < lines.length
                            && !lines[j].trim().isEmpty()
                            && !KNOWLEDGE_LINE.matcher(lines[j].trim()).matches()) {
                        content.append('\n').append(lines[j]);
                        j++;
                    }
                    knowledge.add(new KnowledgeEntry(category, title, content.toString()));
                    i = j;
                } else {
                    i++;
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
    // Real bug found via live browser testing: "I don't want a package. What sandwiches do you
    // have?" was matched to the DINNER-PACKAGES knowledge entry, not Sandwiches — for two
    // compounding reasons, both fixed below. (1) The word "package" inside "don't want a
    // package" was still counted as a positive topical signal — a negated mention should never
    // count toward relevance at all. (2) Scoring counted one point per OCCURRENCE of a matching
    // word in the knowledge entry's own text, so a longer entry that happens to repeat a common
    // word many times (the packages summary says "package" a dozen times) could out-score a
    // short, genuinely on-topic entry that mentions the query's real subject only once. Scoring
    // now counts at most once per distinct QUERY word, and negated spans are stripped from the
    // query text before it's ever tokenized.
    private static final Pattern NEGATED_MENTION = Pattern.compile(
            "(?:don'?t want|do not want|not|without|skip|no)\\s+(?:a\\s+|the\\s+|an\\s+)?\\w+",
            Pattern.CASE_INSENSITIVE);

    private KnowledgeEntry bestKnowledgeMatch(SystemPromptFacts facts, String text) {
        String stripped = NEGATED_MENTION.matcher(text).replaceAll(" ");
        List<String> queryWords = List.of(stripped.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"));
        KnowledgeEntry best = null;
        int bestScore = 0;
        for (KnowledgeEntry entry : facts.knowledge) {
            Set<String> titleWords = new HashSet<>(List.of(entry.title.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")));
            Set<String> bodyWords = new HashSet<>(List.of(entry.content.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")));
            int score = 0;
            for (String qw : queryWords) {
                if (qw.length() <= 3) continue;
                // Word-stem match in either direction ("open" <-> "opening", "hour" <-> "hours")
                // rather than requiring the query to literally contain the whole knowledge word
                // verbatim. A title match counts far more than a content-only match — real bug
                // found via live browser testing: "what sandwiches do you have" was matched to a
                // generic "you can order any individual item..." blurb that happens to list
                // "sandwiches" as one word among many category names, instead of the dedicated
                // Sandwiches entry, because both scored an equal single content-word hit; the
                // TITLE "Sandwiches" is the far stronger, more specific signal of the two.
                boolean titleMatch = titleWords.stream()
                        .anyMatch(kw -> kw.length() > 3 && (kw.startsWith(qw) || qw.startsWith(kw)));
                boolean bodyMatch = !titleMatch && bodyWords.stream()
                        .anyMatch(kw -> kw.length() > 3 && (kw.startsWith(qw) || qw.startsWith(kw)));
                if (titleMatch) score += 5;
                else if (bodyMatch) score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = entry;
            }
        }
        return bestScore > 0 ? best : null;
    }

    // Parses the same real "Name — GH₵Price[ — Description]" lines this class already uses
    // elsewhere (see MENU_ITEM_LINE) and picks the genuine minimum — never a guess.
    private String cheapestItemIn(String categoryContent) {
        String bestLine = null;
        java.math.BigDecimal bestPrice = null;
        Pattern priceLine = Pattern.compile("^(.+?)\\s*—\\s*GH₵([0-9.]+)");
        for (String line : categoryContent.split("\n")) {
            Matcher m = priceLine.matcher(line.trim());
            if (!m.find()) continue;
            java.math.BigDecimal price = new java.math.BigDecimal(m.group(2));
            if (bestPrice == null || price.compareTo(bestPrice) < 0) {
                bestPrice = price;
                bestLine = line.trim();
            }
        }
        return bestLine != null ? "Our cheapest option there is " + bestLine + "." : null;
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
