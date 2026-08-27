package com.ratel.rbms.service;

import com.ratel.rbms.dto.DemoSeedResponse;
import com.ratel.rbms.entity.AiKnowledgeEntry;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.AiKnowledgeEntryRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Builds the fictional "Paradise Beach Resort (Demo)" business used to
 * demonstrate the full Tallia AI conversation -> tool -> RBMS loop to a
 * prospective client, entirely through existing RBMS tables (Business,
 * ServiceType/ServiceCatalogItem, Customer, AiKnowledgeEntry) — no demo-
 * specific schema, no second database, nothing the AI itself couldn't
 * equally do for a real client's own data.
 *
 * Idempotent by business slug: re-running this after the business already
 * exists is a safe no-op that just returns the existing business's info,
 * never a second copy. Only ever reachable through
 * PlatformDemoController — Super-Admin-only AND gated behind
 * app.demo.seed-enabled (off by default everywhere, including production).
 *
 * Deliberately does NOT enable the "AI" module for the seeded business —
 * that stays a separate, explicit Super Admin action through the existing
 * module-toggle mechanism (PlatformBusinessService.updateEnabledModules),
 * same as it would be for a real client, so the demo also proves out that
 * real mechanism rather than special-casing it away.
 */
@Service
public class DemoSeedService {

    // Fixed, deliberately fictional — printed back in the seed response so
    // whoever runs the demo can log in immediately. Not a secret: this
    // account only ever exists on a business named "(Demo)" that nothing
    // else in the system depends on, on a feature-flagged, Super-Admin-only
    // endpoint that's off by default in every environment including
    // production.
    private static final String DEMO_SLUG = "paradise-beach-resort-demo";
    private static final String DEMO_OWNER_EMAIL = "demo.owner@paradise-beach-resort.example";
    private static final String DEMO_OWNER_PASSWORD = "DemoResort123!";

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final CustomerRepository customerRepository;
    private final AiKnowledgeEntryRepository aiKnowledgeEntryRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoSeedService(
            BusinessRepository businessRepository,
            UserRepository userRepository,
            ServiceTypeRepository serviceTypeRepository,
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            CustomerRepository customerRepository,
            AiKnowledgeEntryRepository aiKnowledgeEntryRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.customerRepository = customerRepository;
        this.aiKnowledgeEntryRepository = aiKnowledgeEntryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public DemoSeedResponse seedParadiseBeachResort(boolean demoEnabled) {
        if (!demoEnabled) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Demo seeding isn't enabled on this server.");
        }

        var existing = businessRepository.findBySlug(DEMO_SLUG);
        if (existing.isPresent()) {
            return new DemoSeedResponse(existing.get().getId(), DEMO_SLUG, DEMO_OWNER_EMAIL, DEMO_OWNER_PASSWORD, false);
        }

        Business business = businessRepository.save(Business.builder()
                .name("Paradise Beach Resort (Demo)")
                .slug(DEMO_SLUG)
                .industry(Industry.OTHER)
                .currency("GHS")
                .location("Labadi Beach Road, Accra, Ghana (fictional demo address)")
                .contactEmail("hello@paradise-beach-resort.example")
                .contactPhone("0244000000")
                .build());

        userRepository.save(User.builder()
                .businessId(business.getId())
                .fullName("Paradise Resort Demo Owner")
                .email(DEMO_OWNER_EMAIL)
                .passwordHash(passwordEncoder.encode(DEMO_OWNER_PASSWORD))
                .role(Role.OWNER)
                .build());

        seedServices(business.getId());
        seedCustomers(business.getId());
        seedKnowledge(business.getId());

        return new DemoSeedResponse(business.getId(), DEMO_SLUG, DEMO_OWNER_EMAIL, DEMO_OWNER_PASSWORD, true);
    }

    // ---- Services — fictional demo prices/durations/capacity, fully
    // editable afterward through the normal Inventory/Services UI like any
    // other business's catalogue. maxConcurrentBookings stands in for the
    // "capacity" figures in the spec, since that's the field the real
    // availability logic (BookingService.checkAvailability) actually
    // enforces — this system has no separate "guests per booking" field,
    // so a party size mentioned in conversation is carried in the
    // booking's notes, not a structured headcount. ----

    private void seedServices(UUID businessId) {
        ServiceType type = serviceTypeRepository.save(ServiceType.builder()
                .businessId(businessId)
                .name("Resort Experiences")
                .build());

        record Demo(String name, String description, BigDecimal price, int durationMinutes, int maxConcurrent) {
        }

        List<Demo> demos = List.of(
                new Demo("Beach Day Pass", "Full-day access to the private beach, loungers, and changing facilities.",
                        new BigDecimal("50.00"), 480, 100),
                new Demo("Private Cabana", "A shaded private cabana for up to a small group, 4-hour slot.",
                        new BigDecimal("300.00"), 240, 8),
                new Demo("Restaurant Reservation", "A reserved table at our beachfront restaurant.",
                        BigDecimal.ZERO, 120, 50),
                new Demo("Birthday Beach Package", "A reserved beach area with decorations and a dedicated host.",
                        new BigDecimal("1500.00"), 240, 30),
                new Demo("Corporate Beach Package", "Meeting space, catering options, and team activity areas.",
                        new BigDecimal("3500.00"), 360, 80)
        );

        for (Demo d : demos) {
            serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                    .businessId(businessId)
                    .serviceTypeId(type.getId())
                    .name(d.name())
                    .price(d.price())
                    .active(true)
                    .bookableOnline(true)
                    .durationMinutes(d.durationMinutes())
                    .maxConcurrentBookings(d.maxConcurrent())
                    .requiresLocation(false)
                    .build());
        }
    }

    // ---- Customers — obviously fictional test numbers, purely to
    // demonstrate the AI recognizing a returning customer by phone. ----

    private void seedCustomers(UUID businessId) {
        record Demo(String name, String phone) {
        }
        List<Demo> demos = List.of(
                new Demo("Ama Mensah", "0244000001"),
                new Demo("Kojo Owusu", "0244000002")
        );
        for (Demo d : demos) {
            String normalized = com.ratel.rbms.util.PhoneUtils.normalize(d.phone());
            if (customerRepository.findFirstByBusinessIdAndPhoneNormalized(businessId, normalized).isPresent()) continue;
            customerRepository.save(Customer.builder()
                    .businessId(businessId)
                    .fullName(d.name())
                    .phone(d.phone())
                    .source("DEMO_SEED")
                    .build());
        }
    }

    // ---- Knowledge base — realistic but clearly fictional; the AI must
    // never invent hotel availability, so that's stated as explicitly not
    // bookable rather than left for the model to guess about. ----

    private void seedKnowledge(UUID businessId) {
        record Demo(String title, String content, String category) {
        }
        List<Demo> demos = List.of(
                new Demo("Welcome", "Welcome to Paradise Beach Resort — a beachfront getaway for day visits, "
                        + "events, and relaxation by the sea.", "BUSINESS_INFO"),
                new Demo("Location", "We're located on Labadi Beach Road, Accra, Ghana.", "BUSINESS_INFO"),
                new Demo("Opening hours", "Paradise Beach Resort is open daily from 8:00 AM to 10:00 PM.", "FAQ"),
                new Demo("Parking", "Free on-site parking is available for all guests, including space for tour buses.", "FAQ"),
                new Demo("Wi-Fi", "Complimentary Wi-Fi covers the beach and restaurant area — ask any staff member for the password.", "FAQ"),
                new Demo("Facilities", "Our facilities include a private beach, cabanas, a beachfront restaurant, "
                        + "changing rooms, showers, and a kids' play area.", "BUSINESS_INFO"),
                new Demo("Beach rules", "Life jackets are provided and required for water activities. Glass "
                        + "containers aren't permitted on the beach for safety.", "POLICY"),

                new Demo("Restaurant hours", "Our restaurant serves breakfast, lunch, and dinner daily from "
                        + "8:00 AM to 9:30 PM.", "RESTAURANT"),
                new Demo("Dining", "Our restaurant offers fresh seafood and grilled specialties, with both "
                        + "indoor and beachfront seating.", "RESTAURANT"),
                new Demo("Sample menu", "Grilled Tilapia GH₵80, Jollof Rice with Chicken GH₵60, Seafood Platter "
                        + "GH₵150, Fresh Coconut GH₵15.", "RESTAURANT"),
                new Demo("Restaurant reservations", "Reservations are recommended for groups of 6 or more, "
                        + "especially on weekends.", "RESTAURANT"),

                new Demo("Birthday events", "Our Birthday Beach Package includes a reserved beach area, "
                        + "decorations, and a dedicated host for up to 30 guests.", "EVENTS"),
                new Demo("Corporate events", "Our Corporate Beach Package includes meeting space, catering "
                        + "options, and team activity areas for up to 80 guests.", "EVENTS"),
                new Demo("Weddings", "We host beach weddings by special arrangement — this isn't available to "
                        + "book online yet, so please connect with our events team.", "EVENTS"),
                new Demo("Group bookings", "Groups of 15 or more should contact our events team in advance so "
                        + "we can prepare adequate space and staffing.", "EVENTS"),

                new Demo("Hotel accommodation", "Our hotel accommodation is currently under development and is "
                        + "not yet available for booking. Please check back soon.", "HOTEL"),

                new Demo("Cancellation policy", "Bookings can be cancelled or rescheduled free of charge up to "
                        + "24 hours before your visit.", "POLICY"),
                new Demo("Outside food policy", "Outside food and drinks aren't permitted on the beach, but are "
                        + "welcome at our picnic area near the car park.", "POLICY"),
                new Demo("Children policy", "Children are welcome throughout the resort; children under 12 "
                        + "receive free entry to the Beach Day Pass when accompanied by a paying adult.", "POLICY"),
                new Demo("Pets policy", "Pets are welcome on the beach on a leash, but aren't permitted inside "
                        + "the restaurant.", "POLICY"),
                new Demo("Large groups policy", "Groups larger than 15 people should book at least 3 days in "
                        + "advance to guarantee space.", "POLICY"),
                new Demo("Event deposit policy", "A 50% deposit is required to confirm any event package "
                        + "booking (birthday, corporate, or wedding).", "POLICY")
        );

        for (Demo d : demos) {
            aiKnowledgeEntryRepository.save(AiKnowledgeEntry.builder()
                    .businessId(businessId)
                    .title(d.title())
                    .content(d.content())
                    .category(d.category())
                    .active(true)
                    .build());
        }
    }
}
