package com.ratel.rbms.service;

import com.ratel.rbms.dto.DemoSeedResponse;
import com.ratel.rbms.entity.AiKnowledgeEntry;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessWorkingHours;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.Option;
import com.ratel.rbms.entity.PackageComponent;
import com.ratel.rbms.entity.Policy;
import com.ratel.rbms.entity.PolicyVersion;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServicePackage;
import com.ratel.rbms.entity.ServicePackageItem;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.SubstitutionRule;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.AiKnowledgeEntryRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.BusinessWorkingHoursRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.OfferingRepository;
import com.ratel.rbms.repository.OptionRepository;
import com.ratel.rbms.repository.PackageComponentRepository;
import com.ratel.rbms.repository.PolicyRepository;
import com.ratel.rbms.repository.PolicyVersionRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServicePackageItemRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.repository.SubstitutionRuleRepository;
import com.ratel.rbms.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Restaurant-AI-demo phase — builds the dedicated "Cafe Bar Noir" demo tenant the client demo
 * runs against, entirely through the same real tables/services every other business uses:
 * Business/User (this file), ServiceType/ServiceCatalogItem/AiKnowledgeEntry (regular menu),
 * ServicePackage + the canonical Offering/PackageComponent/Option/SubstitutionRule model (Phase
 * 2/5A/5B, unmodified) for the four demo dinner packages, Policy/PolicyVersion (Phase 3,
 * unmodified) for the real reservation policy, and the Phase 5C cutover lifecycle
 * (Stage1VerificationService -> BookingCutoverStateResolver) so this one business's packages
 * actually price/substitute through the canonical path AiToolService's new package tools read.
 *
 * <p>Mirrors {@link DemoSeedService}'s own established conventions exactly: idempotent by business
 * slug (a second call is a safe no-op returning the existing business, never a duplicate), a
 * fixed fictional owner login returned in the response, the AI module deliberately left
 * disabled (a separate, explicit Super-Admin step — same as onboarding any real client), and
 * reachable only through a demo-seeding endpoint gated behind app.demo.seed-enabled AND
 * Super-Admin-only. To reset during development: delete this business (cascades to every row
 * this class wrote, via the existing ON DELETE CASCADE chain rooted at businesses.id) and call
 * the seed endpoint again — no separate reset mechanism is introduced.
 *
 * <p>Menu names, descriptions and GH₵ prices below are transcribed verbatim from the restaurant's
 * own supplied menu — never invented. The four dinner packages are explicitly DEMO
 * configurations (labelled as such in their own description/knowledge entries): real menu items
 * combined into plausible dinner packages for the purpose of demonstrating the
 * package-substitution workflow, not a claim about Cafe Bar Noir's actual commercial packages.
 * The reservation policy content is transcribed verbatim from the restaurant's own supplied
 * policy document.
 */
@Service
public class CafeBarNoirDemoSeedService {

    private static final String DEMO_SLUG = "cafe-bar-noir-demo";
    private static final String DEMO_OWNER_EMAIL = "demo.owner@cafe-bar-noir.example";
    private static final String DEMO_OWNER_PASSWORD = "CafeBarNoir123!";

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final ServicePackageItemRepository servicePackageItemRepository;
    private final OfferingRepository offeringRepository;
    private final PackageComponentRepository packageComponentRepository;
    private final OptionRepository optionRepository;
    private final SubstitutionRuleRepository substitutionRuleRepository;
    private final PolicyRepository policyRepository;
    private final PolicyVersionRepository policyVersionRepository;
    private final AiKnowledgeEntryRepository aiKnowledgeEntryRepository;
    private final CustomerRepository customerRepository;
    private final BusinessWorkingHoursRepository businessWorkingHoursRepository;
    private final OfferingSyncService offeringSyncService;
    private final BookingCutoverStateResolver bookingCutoverStateResolver;
    private final Stage1VerificationService stage1VerificationService;
    private final PasswordEncoder passwordEncoder;

    public CafeBarNoirDemoSeedService(
            BusinessRepository businessRepository,
            UserRepository userRepository,
            ServiceTypeRepository serviceTypeRepository,
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            ServicePackageRepository servicePackageRepository,
            ServicePackageItemRepository servicePackageItemRepository,
            OfferingRepository offeringRepository,
            PackageComponentRepository packageComponentRepository,
            OptionRepository optionRepository,
            SubstitutionRuleRepository substitutionRuleRepository,
            PolicyRepository policyRepository,
            PolicyVersionRepository policyVersionRepository,
            AiKnowledgeEntryRepository aiKnowledgeEntryRepository,
            CustomerRepository customerRepository,
            BusinessWorkingHoursRepository businessWorkingHoursRepository,
            OfferingSyncService offeringSyncService,
            BookingCutoverStateResolver bookingCutoverStateResolver,
            Stage1VerificationService stage1VerificationService,
            PasswordEncoder passwordEncoder
    ) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.servicePackageRepository = servicePackageRepository;
        this.servicePackageItemRepository = servicePackageItemRepository;
        this.offeringRepository = offeringRepository;
        this.packageComponentRepository = packageComponentRepository;
        this.optionRepository = optionRepository;
        this.substitutionRuleRepository = substitutionRuleRepository;
        this.policyRepository = policyRepository;
        this.policyVersionRepository = policyVersionRepository;
        this.aiKnowledgeEntryRepository = aiKnowledgeEntryRepository;
        this.customerRepository = customerRepository;
        this.businessWorkingHoursRepository = businessWorkingHoursRepository;
        this.offeringSyncService = offeringSyncService;
        this.bookingCutoverStateResolver = bookingCutoverStateResolver;
        this.stage1VerificationService = stage1VerificationService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public DemoSeedResponse seedCafeBarNoir(boolean demoEnabled) {
        if (!demoEnabled) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Demo seeding isn't enabled on this server.");
        }

        var existing = businessRepository.findBySlug(DEMO_SLUG);
        if (existing.isPresent()) {
            return new DemoSeedResponse(existing.get().getId(), DEMO_SLUG, DEMO_OWNER_EMAIL, DEMO_OWNER_PASSWORD, false);
        }

        Business business = businessRepository.save(Business.builder()
                .name("Cafe Bar Noir")
                .slug(DEMO_SLUG)
                .industry(Industry.RESTAURANT)
                .currency("GHS")
                // Fictional, clearly-demo contact details — same posture as DemoSeedService's own
                // "(fictional demo address)" precedent. Deliberately NOT a real Cafe Bar Noir
                // address/phone: none was supplied, and inventing one would violate the "never
                // invent missing restaurant information" rule this whole phase is built around.
                .location("Demo address — Accra, Ghana (fictional, for demonstration only)")
                .contactEmail("hello@cafe-bar-noir.example")
                .contactPhone("0244000099")
                .build());

        userRepository.save(User.builder()
                .businessId(business.getId())
                .fullName("Cafe Bar Noir Demo Owner")
                .email(DEMO_OWNER_EMAIL)
                .passwordHash(passwordEncoder.encode(DEMO_OWNER_PASSWORD))
                .role(Role.OWNER)
                .build());
        // Re-read so every subsequent write has a real, persisted owner id to attribute to
        // (Policy/PolicyVersion.createdBy is NOT NULL).
        User owner = userRepository.findByEmail(DEMO_OWNER_EMAIL)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Demo owner failed to persist."));

        seedWorkingHours(business.getId());
        seedRegularMenuKnowledge(business.getId());
        seedPackages(business.getId());
        seedPolicy(business.getId(), owner.getId());
        seedCustomers(business.getId());
        seedBusinessKnowledge(business.getId());

        // Phase 5C cutover — run the SAME real verification/enablement lifecycle any real
        // business goes through, so the packages just seeded are actually served through the
        // canonical Offering/PackagePricingService path (AiToolService's getPackageOptions/
        // previewPackagePricing/createBooking all require useCanonical(businessId) == true).
        VerificationResult verification = stage1VerificationService.verifyBusiness(business.getId());
        bookingCutoverStateResolver.applyVerificationOutcome(business.getId(), verification);
        if (bookingCutoverStateResolver.resolve(business.getId()) == com.ratel.rbms.entity.enums.BookingCutoverState.VERIFIED) {
            bookingCutoverStateResolver.enableCanonical(business.getId());
        } else {
            // Never silently ship a demo tenant whose packages can't actually be booked
            // canonically — a mismatch here means a real bug in this seed data, not something to
            // paper over with a legacy fallback the AI's new package tools don't understand.
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cafe Bar Noir demo data failed canonical verification: " + verification.mismatches());
        }

        return new DemoSeedResponse(business.getId(), DEMO_SLUG, DEMO_OWNER_EMAIL, DEMO_OWNER_PASSWORD, true);
    }

    // ==================== Working hours ====================

    // The restaurant's own policy describes three 4-hour reservation blocks spanning 8am-11pm
    // (8-12, 1-5, 7-11) but BookingService's working-hours model only understands one
    // contiguous per-day start/end window (Phase 1, unmodified) — there is no richer
    // multi-block model anywhere in this codebase yet, for any business. Seeding the widest
    // covering window (8:00-23:00, every day) is the only way an evening dinner reservation
    // (the demo's own main scenario) doesn't get incorrectly rejected as "outside business
    // hours" by that existing, unmodified check. Documented as a known limitation in the final
    // report, not silently papered over.
    private void seedWorkingHours(UUID businessId) {
        for (int day = 1; day <= 7; day++) {
            businessWorkingHoursRepository.save(BusinessWorkingHours.builder()
                    .businessId(businessId)
                    .dayOfWeek(day)
                    .startTime(LocalTime.of(8, 0))
                    .endTime(LocalTime.of(23, 0))
                    .build());
        }
    }

    // ==================== Regular menu — AiKnowledgeEntry only (Phase 1 knowledge architecture,
    // unmodified) — one entry per category, exact supplied names/prices/descriptions, never
    // invented. Not represented as ServiceCatalogItem rows: no real transaction is required for
    // à-la-carte Q&A (the accepted scoping decision — see the phase's own final report). ====================

    private record MenuItem(String name, BigDecimal price, String description) {
        MenuItem(String name, BigDecimal price) {
            this(name, price, null);
        }
    }

    private void seedRegularMenuKnowledge(UUID businessId) {
        Map<String, List<MenuItem>> menu = new java.util.LinkedHashMap<>();

        menu.put("Main Course", List.of(
                new MenuItem("Grilled Pepper Chicken", bd(150), "Well-seasoned half chicken with pepper, grilled to your taste."),
                new MenuItem("Spicy Chicken Wings", bd(155), "Chicken wings seasoned with bell pepper."),
                new MenuItem("Jambalaya", bd(200), "Seasonal seafood mix with tomatoes and herbs sauce."),
                new MenuItem("Pork Chops", bd(220), "Foreign pork grilled with olive oil and herbs."),
                new MenuItem("Chicken Espetada", bd(250), "Deboned chicken thigh on metal skewer."),
                new MenuItem("Steak Espetada", bd(230), "Salt and pepper-seasoned beef fillet on a metal skewer."),
                new MenuItem("Seafood Espetada", bd(300), "Home-seasoned prawns, calamari, and fillet fish on a skewer stand."),
                new MenuItem("Snapper (grilled/fried)", bd(150), "Fried/grilled snapper seasoned with salt, pepper, garlic and ginger."),
                new MenuItem("Seafoods Spaghetti", bd(300), "Calamari, prawns, and fish fillets in a tomato and herb sauce tossed together with spaghetti."),
                new MenuItem("Tilapia (grilled/fried)", bd(170), "Ginger and garlic spice tilapia."),
                new MenuItem("Assorted Fried Rice", bd(200), "Joellen cut beef fillet, chicken breast, and seasonal vegetables into fried rice."),
                new MenuItem("Assorted Jollof Rice", bd(200), "Joellen cut beef fillet, chicken breast, and seasonal vegetables into jollof rice."),
                new MenuItem("Snapper Sauce", bd(170), "Grilled snapper in tomato herb sauce and vegetables."),
                new MenuItem("Beef Sauce", bd(198), "Joellen cut beef fillet in brown sauce and seasonal vegetables.")
        ));
        menu.put("Salads", List.of(
                new MenuItem("Ghanaian Mix Salad", bd(175), "Mother grain with vegetables and herbs with vinaigrette dressing."),
                new MenuItem("Green Salad", bd(140), "Crunchy romaine lettuce, French beans, cucumber and onions with thousand island dressing."),
                new MenuItem("Tuna Salad", bd(186), "Tuna mixed with vegetables and a bed of lettuce with mustard vinaigrette dressing."),
                new MenuItem("Chicken Salad", bd(185), "Seasonal diced vegetables with sautéed chicken breast with house-made mayonnaise dressing."),
                new MenuItem("Seafood Salad", bd(298), "Grilled calamari, grouper fillet and prawns on a bed of lettuce with sweet mustard dressing.")
        ));
        menu.put("Sides", List.of(
                new MenuItem("Mashed potatoes", bd(60)),
                new MenuItem("Sauteed vegetables", bd(65)),
                new MenuItem("Fried rice", bd(75)),
                new MenuItem("Jollof rice", bd(75)),
                new MenuItem("Vegetable rice", bd(75)),
                new MenuItem("Parsley potato", bd(75)),
                new MenuItem("Yam chips", bd(45)),
                new MenuItem("Kelewele", bd(40)),
                new MenuItem("Fried plantain", bd(40)),
                new MenuItem("French fries", bd(40))
        ));
        menu.put("Finger Foods", List.of(
                new MenuItem("Vegetable and beef samosa", bd(50)),
                new MenuItem("Vegetables Spring Rolls", bd(50)),
                new MenuItem("Vegetable kebab", bd(40)),
                new MenuItem("Spicy calamari", bd(197)),
                new MenuItem("Chicken wings only", bd(100))
        ));
        menu.put("Pastas", List.of(
                new MenuItem("Penne Al Polo", bd(220), "Andante penne pasta, mushroom and chicken in cheesy cream sauce."),
                new MenuItem("Assorted Noodles", bd(230), "Shredded chicken, beef fillet, sausages and seasonal vegetables tossed in brown gravy.")
        ));
        menu.put("Desserts", List.of(
                new MenuItem("Apple tart", bd(75)),
                new MenuItem("Cheesecake", bd(95)),
                new MenuItem("Brownies", bd(75)),
                new MenuItem("Double chocolate cake", bd(130)),
                new MenuItem("Fruit salad", bd(45))
        ));
        menu.put("Ice Cream", List.of(
                new MenuItem("Strawberry", bd(50)),
                new MenuItem("Chocolate", bd(50)),
                new MenuItem("Vanilla", bd(50))
        ));
        menu.put("Pizzas", List.of(
                new MenuItem("Vegetable pizza", bd(100)),
                new MenuItem("Chicken pizza", bd(120)),
                new MenuItem("Meat supreme", bd(150)),
                new MenuItem("All seasoned", bd(190))
        ));
        menu.put("Sandwiches", List.of(
                new MenuItem("Tuna Sandwich", bd(100), "Tuna, lettuce, green pepper, onion, tomato and mayonnaise on toasted white or brown bread."),
                new MenuItem("Chicken Sandwich", bd(95), "Sliced chicken breast, lettuce, tomato and mayonnaise on toasted white or brown bread."),
                new MenuItem("Club Sandwich", bd(130), "Bacon, cheese, egg and vegetables on toasted white or brown bread."),
                new MenuItem("Beef Burger", bd(110), "Beef patty, cheese, lettuce, tomato and relish in burger bun."),
                new MenuItem("Chicken Burger", bd(100), "Breaded chicken breast, lettuce, tomatoes, cocktail sauce in burger bun.")
        ));
        menu.put("Build-Your-Platter Items", List.of(
                new MenuItem("Pork chops", bd(160)),
                new MenuItem("Spicy chicken wings", bd(120)),
                new MenuItem("Grilled pepper chicken", bd(110)),
                new MenuItem("Fish fingers", bd(79)),
                new MenuItem("Sweet and spicy wings", bd(100)),
                new MenuItem("Eliza's calamari bite", bd(110)),
                new MenuItem("Chicken kebab", bd(100)),
                new MenuItem("Beef sausage kebab", bd(90)),
                new MenuItem("Breaded fish", bd(96)),
                new MenuItem("Samosa", bd(50)),
                new MenuItem("Spring roll", bd(50)),
                new MenuItem("Fried rice", bd(75)),
                new MenuItem("Jollof rice", bd(75)),
                new MenuItem("Garlic parmesan potato", bd(85)),
                new MenuItem("Parsley potato", bd(75)),
                new MenuItem("Vegetable rice", bd(75)),
                new MenuItem("Potato wedges", bd(45)),
                new MenuItem("Yam chips", bd(45)),
                new MenuItem("French fries", bd(40)),
                new MenuItem("Spicy kelewele", bd(40))
        ));

        for (Map.Entry<String, List<MenuItem>> category : menu.entrySet()) {
            StringBuilder content = new StringBuilder();
            for (MenuItem item : category.getValue()) {
                content.append(item.name()).append(" — GH₵").append(item.price());
                if (item.description() != null) {
                    content.append(" — ").append(item.description());
                }
                content.append('\n');
            }
            aiKnowledgeEntryRepository.save(AiKnowledgeEntry.builder()
                    .businessId(businessId)
                    .title(category.getKey())
                    .content(content.toString().trim())
                    .category("MENU")
                    .active(true)
                    .build());
        }

        aiKnowledgeEntryRepository.save(AiKnowledgeEntry.builder()
                .businessId(businessId)
                .title("Menu note — Grilled Pepper Chicken")
                .content("\"Grilled Pepper Chicken\" is a Main Course item at GH₵150 (a well-seasoned half "
                        + "chicken). The similarly-named \"Grilled pepper chicken\" on the Build-Your-Platter "
                        + "list is a smaller platter portion at GH₵110 — these are two distinct menu entries "
                        + "at two different prices, not the same dish twice.")
                .category("MENU")
                .active(true)
                .build());

        aiKnowledgeEntryRepository.save(AiKnowledgeEntry.builder()
                .businessId(businessId)
                .title("Build-your-own meal")
                .content("Customers can order any individual item from the regular menu above (mains, "
                        + "salads, sides, finger foods, pastas, desserts, ice cream, pizzas, sandwiches, or "
                        + "the Build-Your-Platter list) to build their own meal. Groups can also choose one "
                        + "of our four dinner packages and customise it with substitutions.")
                .category("MENU")
                .active(true)
                .build());
    }

    private static BigDecimal bd(int value) {
        return new BigDecimal(value).setScale(2);
    }

    // ==================== Dinner packages — canonical Offering/PackageComponent/Option/
    // SubstitutionRule (Phase 2/5A/5B, unmodified). Every price below is a real, source-derived
    // menu price (see seedRegularMenuKnowledge above); the PACKAGE COMPOSITIONS themselves are
    // explicitly demo configurations — labelled as such in each package's own description and in
    // a dedicated knowledge entry, never presented as Cafe Bar Noir's actual commercial
    // packages. ====================

    private record MenuRef(String name, BigDecimal price) {
    }

    private record ComponentSpec(String slotName, List<MenuRef> choices) {
        MenuRef defaultChoice() {
            return choices.get(0);
        }
    }

    private record PackageSpec(String name, String description, List<ComponentSpec> components) {
    }

    private void seedPackages(UUID businessId) {
        ServiceType packagesType = serviceTypeRepository.save(ServiceType.builder()
                .businessId(businessId)
                .name("Dinner Packages")
                .build());

        List<PackageSpec> specs = List.of(
                new PackageSpec("Classic Dinner",
                        "A well-rounded dinner package — one main, one side, one salad and one dessert per "
                                + "guest, fully customisable. Priced per guest; tell us your party size when booking. "
                                + "DEMO package configuration.",
                        List.of(
                                new ComponentSpec("Main", List.of(
                                        new MenuRef("Grilled Pepper Chicken", bd(150)),
                                        new MenuRef("Spicy Chicken Wings", bd(155)),
                                        new MenuRef("Snapper", bd(150)),
                                        new MenuRef("Tilapia", bd(170))
                                )),
                                new ComponentSpec("Side", List.of(
                                        new MenuRef("Fried Rice", bd(75)),
                                        new MenuRef("Jollof Rice", bd(75)),
                                        new MenuRef("French Fries", bd(40)),
                                        new MenuRef("Mashed Potatoes", bd(60))
                                )),
                                new ComponentSpec("Salad", List.of(
                                        new MenuRef("Green Salad", bd(140)),
                                        new MenuRef("Ghanaian Mix Salad", bd(175))
                                )),
                                new ComponentSpec("Dessert", List.of(
                                        new MenuRef("Apple Tart", bd(75)),
                                        new MenuRef("Brownies", bd(75)),
                                        new MenuRef("Fruit Salad", bd(45))
                                ))
                        )),
                new PackageSpec("Grill & Gravy",
                        "A grilled-protein-forward dinner package — one premium main, one side, one finger "
                                + "food and one dessert per guest, fully customisable. Priced per guest; tell us "
                                + "your party size when booking. DEMO package configuration.",
                        List.of(
                                new ComponentSpec("Main Protein", List.of(
                                        new MenuRef("Chicken Espetada", bd(250)),
                                        new MenuRef("Steak Espetada", bd(230)),
                                        new MenuRef("Pork Chops", bd(220)),
                                        new MenuRef("Seafood Espetada", bd(300))
                                )),
                                new ComponentSpec("Side", List.of(
                                        new MenuRef("Jollof Rice", bd(75)),
                                        new MenuRef("Fried Rice", bd(75)),
                                        new MenuRef("Parsley Potato", bd(75)),
                                        new MenuRef("Yam Chips", bd(45))
                                )),
                                new ComponentSpec("Finger Food", List.of(
                                        new MenuRef("Samosa", bd(50)),
                                        new MenuRef("Spring Roll", bd(50)),
                                        new MenuRef("Spicy Calamari", bd(197)),
                                        new MenuRef("Chicken Wings", bd(100))
                                )),
                                new ComponentSpec("Dessert", List.of(
                                        new MenuRef("Cheesecake", bd(95)),
                                        new MenuRef("Double Chocolate Cake", bd(130)),
                                        new MenuRef("Brownies", bd(75))
                                ))
                        )),
                new PackageSpec("Seafood Experience",
                        "A seafood-forward dinner package — one seafood main, one side, one salad and one "
                                + "dessert per guest, fully customisable. Priced per guest; tell us your party size "
                                + "when booking. DEMO package configuration.",
                        List.of(
                                new ComponentSpec("Seafood Main", List.of(
                                        new MenuRef("Seafood Espetada", bd(300)),
                                        new MenuRef("Jambalaya", bd(200)),
                                        new MenuRef("Seafoods Spaghetti", bd(300)),
                                        new MenuRef("Snapper", bd(150)),
                                        new MenuRef("Tilapia", bd(170))
                                )),
                                new ComponentSpec("Side", List.of(
                                        new MenuRef("Vegetable Rice", bd(75)),
                                        new MenuRef("Fried Rice", bd(75)),
                                        new MenuRef("Jollof Rice", bd(75)),
                                        new MenuRef("Sauteed Vegetables", bd(65))
                                )),
                                new ComponentSpec("Salad", List.of(
                                        new MenuRef("Seafood Salad", bd(298)),
                                        new MenuRef("Tuna Salad", bd(186)),
                                        new MenuRef("Green Salad", bd(140))
                                )),
                                new ComponentSpec("Dessert", List.of(
                                        new MenuRef("Cheesecake", bd(95)),
                                        new MenuRef("Fruit Salad", bd(45)),
                                        new MenuRef("Apple Tart", bd(75))
                                ))
                        )),
                new PackageSpec("Eliza Signature Feast",
                        "Our premium dinner package — one premium main, one side, one salad, one finger "
                                + "food and one dessert per guest, fully customisable. Priced per guest; tell us "
                                + "your party size when booking. DEMO package configuration.",
                        List.of(
                                new ComponentSpec("Premium Main", List.of(
                                        new MenuRef("Seafood Espetada", bd(300)),
                                        new MenuRef("Steak Espetada", bd(230)),
                                        new MenuRef("Chicken Espetada", bd(250)),
                                        new MenuRef("Jambalaya", bd(200))
                                )),
                                new ComponentSpec("Side", List.of(
                                        new MenuRef("Garlic Parmesan Potato", bd(85)),
                                        new MenuRef("Jollof Rice", bd(75)),
                                        new MenuRef("Fried Rice", bd(75)),
                                        new MenuRef("Parsley Potato", bd(75))
                                )),
                                new ComponentSpec("Salad", List.of(
                                        new MenuRef("Seafood Salad", bd(298)),
                                        new MenuRef("Chicken Salad", bd(185)),
                                        new MenuRef("Ghanaian Mix Salad", bd(175))
                                )),
                                new ComponentSpec("Finger Food", List.of(
                                        new MenuRef("Spicy Calamari", bd(197)),
                                        new MenuRef("Chicken Wings", bd(100)),
                                        new MenuRef("Samosa", bd(50)),
                                        new MenuRef("Spring Roll", bd(50))
                                )),
                                new ComponentSpec("Dessert", List.of(
                                        new MenuRef("Double Chocolate Cake", bd(130)),
                                        new MenuRef("Cheesecake", bd(95)),
                                        new MenuRef("Brownies", bd(75))
                                ))
                        ))
        );

        // Reused across packages whose defaults happen to name the same real menu item (e.g.
        // "Cheesecake" defaults both Grill & Gravy's... no, Seafood Experience's dessert AND
        // Grill & Gravy's own — see each PackageSpec above) — one real ServiceCatalogItem per
        // distinct menu item, exactly like a real restaurant's own catalogue would have, rather
        // than a duplicate row per package.
        Map<String, ServiceCatalogItem> catalogItemsByName = new HashMap<>();
        ServiceType menuItemsType = serviceTypeRepository.save(ServiceType.builder()
                .businessId(businessId)
                .name("Cafe Bar Noir Package Menu Items")
                .build());

        List<String> packageKnowledgeLines = new ArrayList<>();

        for (PackageSpec spec : specs) {
            BigDecimal basePrice = spec.components().stream()
                    .map(c -> c.defaultChoice().price())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ServicePackage pkg = ServicePackage.builder()
                    .businessId(businessId)
                    .serviceTypeId(packagesType.getId())
                    .name(spec.name())
                    .description(spec.description())
                    .price(basePrice)
                    .active(true)
                    .bookableOnline(true)
                    .durationMinutes(240) // one reservation time block, per the restaurant's own policy
                    .maxConcurrentBookings(5)
                    .build();
            pkg = servicePackageRepository.save(pkg);
            offeringSyncService.syncServicePackage(pkg);
            Offering offering = offeringRepository.findByBusinessIdAndLegacyServicePackageId(businessId, pkg.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Offering sync failed for " + spec.name()));

            StringBuilder componentSummary = new StringBuilder();
            int displayOrder = 0;
            for (ComponentSpec componentSpec : spec.components()) {
                MenuRef defaultRef = componentSpec.defaultChoice();

                PackageComponent component = packageComponentRepository.save(PackageComponent.builder()
                        .businessId(businessId)
                        .offeringId(offering.getId())
                        .slotName(componentSpec.slotName())
                        .componentKind("SELECTION")
                        .required(true)
                        .minSelections(1)
                        .maxSelections(1)
                        .displayOrder(displayOrder++)
                        .build());

                Option defaultOption = optionRepository.save(Option.builder()
                        .businessId(businessId)
                        .componentId(component.getId())
                        .label(defaultRef.name())
                        .priceAdjustment(BigDecimal.ZERO.setScale(2))
                        .build());
                component.setDefaultOptionId(defaultOption.getId());
                packageComponentRepository.save(component);

                ServiceCatalogItem defaultCatalogItem = catalogItemsByName.computeIfAbsent(defaultRef.name(), name ->
                        serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                                .businessId(businessId)
                                .serviceTypeId(menuItemsType.getId())
                                .name(name)
                                .price(defaultRef.price())
                                .active(true)
                                .bookableOnline(false) // package-internal only — never independently bookable
                                .build()));
                servicePackageItemRepository.save(ServicePackageItem.builder()
                        .packageId(pkg.getId())
                        .serviceCatalogId(defaultCatalogItem.getId())
                        .quantity(1)
                        .build());

                componentSummary.append(componentSpec.slotName()).append(": default ").append(defaultRef.name());
                List<String> altNames = new ArrayList<>();
                for (int i = 1; i < componentSpec.choices().size(); i++) {
                    MenuRef altRef = componentSpec.choices().get(i);
                    Option altOption = optionRepository.save(Option.builder()
                            .businessId(businessId)
                            .componentId(component.getId())
                            .label(altRef.name())
                            .priceAdjustment(BigDecimal.ZERO.setScale(2))
                            .build());
                    BigDecimal priceDelta = altRef.price().subtract(defaultRef.price()).setScale(2);
                    substitutionRuleRepository.save(SubstitutionRule.builder()
                            .businessId(businessId)
                            .fromOptionId(defaultOption.getId())
                            .toOptionId(altOption.getId())
                            .priceDelta(priceDelta)
                            .maxCount(1)
                            .build());
                    altNames.add(altRef.name() + " (" + (priceDelta.signum() >= 0 ? "+" : "") + priceDelta + ")");
                }
                if (!altNames.isEmpty()) {
                    componentSummary.append(" — can substitute for ").append(String.join(", ", altNames));
                }
                componentSummary.append('\n');
            }

            packageKnowledgeLines.add(spec.name() + " — GH₵" + basePrice + " per guest (DEMO package price). "
                    + spec.description() + "\n" + componentSummary);
        }

        aiKnowledgeEntryRepository.save(AiKnowledgeEntry.builder()
                .businessId(businessId)
                .title("Dinner packages (DEMO configurations)")
                .content("Cafe Bar Noir offers four dinner packages for groups, each priced per guest — the "
                        + "final total is the per-guest price times the number of guests. Every component listed "
                        + "below can be substituted for one of its alternatives; substituting changes the price "
                        + "by the amount shown. These four packages are DEMONSTRATION configurations built from "
                        + "real menu items, not a claim about Cafe Bar Noir's official commercial package "
                        + "lineup.\n\n" + String.join("\n", packageKnowledgeLines))
                .category("PACKAGES")
                .active(true)
                .build());
    }

    // ==================== Policy — real Policy/PolicyVersion rows (Phase 3, unmodified),
    // enforced through the real PolicyEngine gate at booking time. Content transcribed verbatim
    // from the restaurant's own supplied reservation policy document — never paraphrased in a
    // way that changes meaning, never invented. Also mirrored into AiKnowledgeEntry rows so
    // ordinary policy QUESTIONS (not just the booking gate) are answerable from the same
    // authoritative text. ====================

    private void seedPolicy(UUID businessId, UUID ownerId) {
        Policy policy = policyRepository.save(Policy.builder()
                .businessId(businessId)
                .policyKey("CAFE_BAR_NOIR_RESERVATION_POLICY")
                .appliesToAction("BOOKING_CREATE")
                .offeringId(null) // business-wide — applies to every package/reservation
                .active(true)
                .createdBy(ownerId)
                .build());

        String content = String.join("\n\n",
                "MENU POLICY\n"
                        + "- Menu selections, including cake details, must be finalised 24 hours before the event date.\n"
                        + "- Clients are allowed to swap food items within related packages.\n"
                        + "- The client must understand the swap rules.\n"
                        + "- Adding items to the menu comes at an extra cost.\n"
                        + "- Special dietary needs should be communicated to the restaurant.\n"
                        + "- In the event of a reaction, the restaurant will not be held responsible for the client's negligence.\n"
                        + "- For buffet packages such as Combo, Classic and Sunday Brunch, starters are shared rather than individually served.\n"
                        + "- Snacks and drinks are not allowed from outside.\n"
                        + "- Cakes from outside are allowed at a fee of GH₵200, non-negotiable.\n"
                        + "- The restaurant is not responsible in case of food poisoning caused by food purchased from outside.\n"
                        + "- Alternatively, the client can negotiate a price reduction depending on the quantity of drinks purchased.",
                "EVENT DURATION\n"
                        + "- The restaurant does not charge for space.\n"
                        + "- Events are scheduled in 4-hour blocks: 8am-12pm, 1pm-5pm, 7pm-11pm.\n"
                        + "- Clients are expected to arrive on time. Lateness will not be overlooked.\n"
                        + "- Once the allocated hours are exhausted, guests have to leave the space.\n"
                        + "- Food packaging is an extra cost of GH₵5 per pack.\n"
                        + "- If the client does not show up after 4 hours, the reservation is marked as completed and the food is forfeited.\n"
                        + "- Extreme lateness may result in an additional space charge of GH₵1,000 if the client wants to go ahead with the event.",
                "DECOR AND SETUP\n"
                        + "- The restaurant handles table setup. Décor comes at an extra cost.\n"
                        + "- Clients can choose to have service in-house or use external decorators.\n"
                        + "- An exception is made for outdoor events where external decorators are allowed.\n"
                        + "- For packages that include painting, the client has to decide on the painting before or after eating.\n"
                        + "- Painting setup comes without tablecloth and napkins but includes the food setup.\n"
                        + "- Clients may choose preferred colours for table setup; the restaurant will use other stated colours where the requested colours are unavailable or insufficient.",
                "CONFIRMATION OF RESERVATION\n"
                        + "- Only payment confirms a reservation.\n"
                        + "- A 70% deposit of the total package cost is required to secure the booking.\n"
                        + "- The remaining 30% must be paid before the event date.\n"
                        + "- Payment options: Cash, Mobile Money, Bank Transfer, POS.",
                "CONFIRMATION OF DETAILS\n"
                        + "- Menu selection and other details must be confirmed no later than one day before the event date.\n"
                        + "- Clients should finalise details on time to give the restaurant sufficient preparation time.\n"
                        + "- If the client fails to confirm details on time, the restaurant will not be responsible for mishaps on the event day.\n"
                        + "- During confirmation, clients should scrutinise messages and notify the reservations team of any unapproved changes to their details.\n"
                        + "- After 2pm, no further changes can be made to reservation details.\n"
                        + "- Same-day reservations are allowed only if payment and event details are sorted at least 4 hours before the start time.",
                "PARKING\n"
                        + "- The restaurant has a designated parking area at its entrance. Security is tasked with patrolling the car park.\n"
                        + "- Guests should not leave valuables in vehicles.\n"
                        + "- The restaurant will not be held responsible for unfortunate incidents such as damage or loss occurring to vehicles.",
                "CONDUCT\n"
                        + "- The restaurant is responsible for staff behaviour, while clients are responsible for the conduct of their guests.\n"
                        + "- Any damage to property or glassware caused by the client or guests is the client's responsibility.\n"
                        + "- Communication should be respectful.\n"
                        + "- Management reserves the right to end an event where there is a violation of policies or disruptive behaviour from guests.",
                "REFUND / CANCELLATION\n"
                        + "- In the event of cancellation, 60% of the deposited amount is refundable.\n"
                        + "- 40% of the deposit covers the loss incurred due to unavailability of the space for potential reservations.\n"
                        + "- This applies to pre-bookings of at least 3 weeks.\n"
                        + "- Clients should give at least one week's notice to avoid losing a further percentage of the refundable amount.\n"
                        + "- Cancelling events booked outside the prebooking period stated above will not attract a refund. Same-day event bookings are not exempt from this rule.\n"
                        + "- In unforeseen circumstances such as accidents or emergencies requiring cancellation, the restaurant will work with the client to reschedule the event or work out a favourable refund percentage for both parties.\n"
                        + "- Refund payment typically takes 10 working days."
        );

        policyVersionRepository.save(PolicyVersion.builder()
                .businessId(businessId)
                .policyId(policy.getId())
                .versionNumber(1)
                .title("Cafe Bar Noir Reservation Policies")
                .content(content)
                .requiresAcknowledgement(true)
                .blocksTransaction(true)
                .staffOverridable(false)
                .createdBy(ownerId)
                .build());

        // Mirrored into knowledge entries (Phase 1 architecture) so ordinary policy QUESTIONS —
        // not just the pre-booking acknowledgement gate — are answerable from the exact same
        // authoritative wording, split by topic so the AI can surface only what's relevant
        // instead of reciting the whole document every time (per the phase's own requirement).
        record Topic(String title, String body) {
        }
        List<Topic> topics = List.of(
                new Topic("Policy — Menu", "Menu selections, including cake details, must be finalised 24 hours before the event date. "
                        + "Clients may swap food items within related packages. Adding items to the menu comes at an extra cost. "
                        + "Special dietary needs should be communicated in advance; the restaurant is not responsible for a reaction "
                        + "caused by the client's own negligence. Snacks and drinks are not allowed from outside. Cakes from outside "
                        + "are allowed at a fee of GH₵200, non-negotiable; the restaurant is not responsible for food poisoning from "
                        + "outside food. A price reduction can be negotiated depending on the quantity of drinks purchased."),
                new Topic("Policy — Event duration", "Reservations are scheduled in 4-hour blocks: 8am-12pm, 1pm-5pm, or 7pm-11pm. There is "
                        + "no charge for the space itself. Guests must leave once the block ends. Food packaging costs GH₵5 per pack. "
                        + "If the client doesn't show up within 4 hours, the reservation is marked completed and the food is forfeited. "
                        + "Extreme lateness may add a GH₵1,000 space charge."),
                new Topic("Policy — Décor and setup", "The restaurant handles table setup; décor is an extra cost. Clients may use "
                        + "external decorators (in-house service is also available), with an exception made for outdoor events. "
                        + "Preferred table colours can be requested; the restaurant substitutes other available colours if the "
                        + "requested ones are unavailable or insufficient."),
                new Topic("Policy — Deposit and payment", "Only payment confirms a reservation: a 70% deposit of the total package "
                        + "cost secures the booking, with the remaining 30% due before the event date. Accepted payment methods are "
                        + "Cash, Mobile Money, Bank Transfer, and POS."),
                new Topic("Policy — Confirming details / changes", "Menu selection and other reservation details must be confirmed no "
                        + "later than one day before the event date. After 2pm, no further changes can be made to reservation details. "
                        + "Same-day reservations are only accepted if payment and details are finalised at least 4 hours before the "
                        + "start time. Confirming late means the restaurant isn't responsible for mishaps on the event day."),
                new Topic("Policy — Parking", "A designated parking area is available at the entrance and is patrolled by security. "
                        + "Guests should not leave valuables in vehicles — the restaurant isn't responsible for damage or loss to vehicles."),
                new Topic("Policy — Conduct", "The restaurant is responsible for its own staff's behaviour; clients are responsible for "
                        + "the conduct of their own guests, including any damage to property or glassware. Management may end an event "
                        + "for policy violations or disruptive behaviour."),
                new Topic("Policy — Cancellation and refunds", "For pre-bookings of at least 3 weeks, cancelling refunds 60% of the "
                        + "deposit (40% covers the lost space); at least one week's notice is needed to avoid losing more of the "
                        + "refundable amount. Cancelling a booking made outside that 3-week pre-booking window is not refundable, "
                        + "including same-day bookings. For genuine emergencies, the restaurant will work with the client on "
                        + "rescheduling or a fair refund. Refunds typically take 10 working days to process.")
        );
        for (Topic topic : topics) {
            aiKnowledgeEntryRepository.save(AiKnowledgeEntry.builder()
                    .businessId(businessId)
                    .title(topic.title())
                    .content(topic.body())
                    .category("POLICY")
                    .active(true)
                    .build());
        }
    }

    // ==================== Demo customers — obviously fictional, matches DemoSeedService's own precedent. ====================

    private void seedCustomers(UUID businessId) {
        record DemoCustomer(String name, String phone) {
        }
        List<DemoCustomer> demoCustomers = List.of(
                new DemoCustomer("Abena Owusu", "0244000010"),
                new DemoCustomer("Kwabena Mensah", "0244000011")
        );
        for (DemoCustomer d : demoCustomers) {
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

    // ==================== General business knowledge — deliberately narrow: only what was
    // actually supplied. Anything not covered here (live music, DJ schedule, Wi-Fi, etc.) is
    // left genuinely absent so the AI correctly says it doesn't know and offers staff escalation
    // (§23 of the phase spec) instead of inventing an answer. ====================

    private void seedBusinessKnowledge(UUID businessId) {
        aiKnowledgeEntryRepository.save(AiKnowledgeEntry.builder()
                .businessId(businessId)
                .title("Welcome")
                .content("Welcome to Cafe Bar Noir — reservations for dinner, drinks, and private events.")
                .category("BUSINESS_INFO")
                .active(true)
                .build());
        aiKnowledgeEntryRepository.save(AiKnowledgeEntry.builder()
                .businessId(businessId)
                .title("Reservation blocks")
                .content("Reservations run in 4-hour blocks: 8am-12pm, 1pm-5pm, or 7pm-11pm. Tell the guest "
                        + "which block their event date/time falls into when confirming details.")
                .category("BUSINESS_INFO")
                .active(true)
                .build());
    }
}
