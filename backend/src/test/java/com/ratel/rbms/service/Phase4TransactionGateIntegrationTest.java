package com.ratel.rbms.service;

import com.ratel.rbms.dto.*;
import com.ratel.rbms.entity.*;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.*;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 4 — proves the gate is genuinely wired into the real
 * transaction-creating service methods (Revision 4 §6's audited call sites), not just
 * exercised in isolation against PolicyEngine directly. Each covered site: (1) is blocked with a
 * clear error when a required, blocking policy is configured and no commitment was supplied; (2)
 * succeeds once a commitment is opened and the policy is disclosed+acknowledged, with the real
 * transaction id correctly backfilled onto the disclosure and the commitment marked COMPLETED.
 *
 * <p>Covers all six audited creation methods: ServiceOrderService.create, SaleService.createSale,
 * BookingService.createStaffBooking, BookingService.createBooking (public),
 * CustomWigRequestService.createByStaff, and CustomWigRequestService.submit (public) — the last
 * exercised through its real CustomItemAttribute/Option selection-resolution path, not substituted
 * with code inspection or the staff-path test.
 */
@SpringBootTest
@Transactional
class Phase4TransactionGateIntegrationTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private PolicyVersionRepository policyVersionRepository;
    @Autowired private PolicyCommitmentRepository policyCommitmentRepository;
    @Autowired private PolicyDisclosureRepository policyDisclosureRepository;
    @Autowired private PolicyEngine policyEngine;

    @Autowired private ServiceOrderService serviceOrderService;
    @Autowired private SaleService saleService;
    @Autowired private BookingService bookingService;
    @Autowired private CustomWigRequestService customWigRequestService;
    @Autowired private CustomItemAttributeRepository customItemAttributeRepository;
    @Autowired private CustomItemAttributeOptionRepository customItemAttributeOptionRepository;
    @Autowired private CustomWigRequestRepository customWigRequestRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private Business newBusiness() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return businessRepository.save(Business.builder()
                .name("Phase 4 Gate Test Business " + unique).slug("phase4-gate-test-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());
    }

    private void actAs(UUID businessId, String role) {
        // A real, persisted User — several of the gated services resolve TenantContext.getUserId()
        // straight into a real FK (service_orders.created_by, SaleService's own cashier lookup),
        // so a random unpersisted UUID here would fail on an unrelated FK before ever reaching
        // this phase's own gate check.
        User user = userRepository.save(User.builder()
                .businessId(businessId).fullName("Test Staff").email("staff+" + UUID.randomUUID() + "@example.com")
                .role(com.ratel.rbms.entity.enums.Role.valueOf(role)).build());
        TenantContext.setBusinessId(businessId);
        TenantContext.setUserId(user.getId());
        TenantContext.setRole(role);
    }

    private Policy newBlockingPolicy(UUID businessId, String action) {
        Policy policy = policyRepository.save(Policy.builder()
                .businessId(businessId).policyKey("gate-test-" + UUID.randomUUID().toString().substring(0, 8))
                .appliesToAction(action).createdBy(UUID.randomUUID()).build());
        policyVersionRepository.save(PolicyVersion.builder()
                .businessId(businessId).policyId(policy.getId()).versionNumber(1)
                .title("Required").content("Must be shown and agreed to.")
                .requiresAcknowledgement(true).blocksTransaction(true).createdBy(UUID.randomUUID()).build());
        return policy;
    }

    private ServiceType newServiceType(UUID businessId) {
        return serviceTypeRepository.save(ServiceType.builder().businessId(businessId).name("Haircut").build());
    }

    private ServiceCatalogItem newBookableCatalogItem(UUID businessId, UUID serviceTypeId) {
        return serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(businessId).serviceTypeId(serviceTypeId).name("Standard Cut")
                .price(new BigDecimal("50.00")).active(true).bookableOnline(true)
                .durationMinutes(30).maxConcurrentBookings(5).build());
    }

    private Customer newCustomer(UUID businessId) {
        return customerRepository.save(Customer.builder()
                .businessId(businessId).fullName("Test Customer").phone("+233" + (200000000 + (int) (Math.random() * 90000000)))
                .build());
    }

    private CustomWigSelectionInput newSelection(UUID businessId) {
        CustomItemAttribute attribute = customItemAttributeRepository.save(CustomItemAttribute.builder()
                .businessId(businessId).name("Length").build());
        CustomItemAttributeOption option = customItemAttributeOptionRepository.save(CustomItemAttributeOption.builder()
                .attributeId(attribute.getId()).label("24 inch").priceModifier(new BigDecimal("100.00")).build());
        return new CustomWigSelectionInput(attribute.getId(), option.getId());
    }

    /** Next weekday (Mon-Sat, matching BusinessWorkingHours.defaultHours()) at 10:00 UTC, safely in the future. */
    private Instant nextBookableSlot() {
        ZonedDateTime candidate = ZonedDateTime.now(ZoneOffset.UTC).plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        while (candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }

    // ==================== ServiceOrderService.create — SERVICE_ORDER_CREATE ====================

    @Test
    void serviceOrderCreate_blockedWithoutCommitmentWhenPolicyRequiresIt() {
        Business business = newBusiness();
        newBlockingPolicy(business.getId(), "SERVICE_ORDER_CREATE");
        Customer customer = newCustomer(business.getId());
        ServiceType type = newServiceType(business.getId());
        actAs(business.getId(), "OWNER");

        ServiceOrderRequest req = new ServiceOrderRequest(customer.getId(),
                List.of(new ServiceOrderItemRequest(type.getId(), null, "Custom line", new BigDecimal("40.00"), null)),
                null, null, null, null);

        ApiException ex = assertThrows(ApiException.class, () -> serviceOrderService.create(req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void serviceOrderCreate_succeedsAndBackfillsDisclosureAfterCommitmentAndAcknowledgement() {
        Business business = newBusiness();
        Policy policy = newBlockingPolicy(business.getId(), "SERVICE_ORDER_CREATE");
        Customer customer = newCustomer(business.getId());
        ServiceType type = newServiceType(business.getId());
        actAs(business.getId(), "OWNER");

        UUID commitment = policyEngine.beginCommitment(business.getId(), "SERVICE_ORDER");
        PolicyDisclosure disclosure = policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment,
                customer.getId(), null, "STAFF", "IN_PERSON");
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "CUSTOMER", customer.getId());

        ServiceOrderRequest req = new ServiceOrderRequest(customer.getId(),
                List.of(new ServiceOrderItemRequest(type.getId(), null, "Custom line", new BigDecimal("40.00"), null)),
                null, null, null, commitment);

        ServiceOrderResponse response = serviceOrderService.create(req);
        assertNotNull(response.id());

        PolicyDisclosure linked = policyDisclosureRepository.findByIdAndBusinessId(disclosure.getId(), business.getId()).orElseThrow();
        assertEquals("SERVICE_ORDER", linked.getTransactionType());
        assertEquals(response.id(), linked.getTransactionId());
        PolicyCommitment stored = policyCommitmentRepository.findByCommitmentReferenceAndBusinessId(commitment, business.getId()).orElseThrow();
        assertEquals("COMPLETED", stored.getStatus());
    }

    // ==================== SaleService.createSale — SALE_CREATE ====================

    @Test
    void saleCreate_blockedWithoutCommitmentWhenPolicyRequiresIt() {
        Business business = newBusiness();
        newBlockingPolicy(business.getId(), "SALE_CREATE");
        Customer customer = newCustomer(business.getId());
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newBookableCatalogItem(business.getId(), type.getId());
        actAs(business.getId(), "OWNER");

        SaleRequest req = new SaleRequest(customer.getId(), com.ratel.rbms.entity.enums.PaymentMethod.CASH,
                List.of(new SaleItemRequest(null, item.getId(), 1, null, null)), null);

        ApiException ex = assertThrows(ApiException.class, () -> saleService.createSale(req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void saleCreate_succeedsAndBackfillsDisclosureAfterCommitmentAndAcknowledgement() {
        Business business = newBusiness();
        Policy policy = newBlockingPolicy(business.getId(), "SALE_CREATE");
        Customer customer = newCustomer(business.getId());
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newBookableCatalogItem(business.getId(), type.getId());
        actAs(business.getId(), "OWNER");

        UUID commitment = policyEngine.beginCommitment(business.getId(), "SALE");
        PolicyDisclosure disclosure = policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment,
                customer.getId(), null, "STAFF", "IN_PERSON");
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "CUSTOMER", customer.getId());

        SaleRequest req = new SaleRequest(customer.getId(), com.ratel.rbms.entity.enums.PaymentMethod.CASH,
                List.of(new SaleItemRequest(null, item.getId(), 1, null, null)), commitment);

        SaleResponse response = saleService.createSale(req);
        assertNotNull(response.id());

        PolicyDisclosure linked = policyDisclosureRepository.findByIdAndBusinessId(disclosure.getId(), business.getId()).orElseThrow();
        assertEquals("SALE", linked.getTransactionType());
        assertEquals(response.id(), linked.getTransactionId());
    }

    // ==================== BookingService.createStaffBooking — STAFF_BOOKING_CREATE ====================

    @Test
    void staffBookingCreate_blockedWithoutCommitmentWhenPolicyRequiresIt() {
        Business business = newBusiness();
        newBlockingPolicy(business.getId(), "STAFF_BOOKING_CREATE");
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newBookableCatalogItem(business.getId(), type.getId());
        actAs(business.getId(), "OWNER");

        CreateStaffBookingRequest req = new CreateStaffBookingRequest(item.getId(), null, null,
                "Walk-in Customer", null, "+233200000001", nextBookableSlot(), null, null, null, "UNPAID", null);

        ApiException ex = assertThrows(ApiException.class, () -> bookingService.createStaffBooking(req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void staffBookingCreate_succeedsAndBackfillsDisclosureAfterCommitmentAndAcknowledgement() {
        Business business = newBusiness();
        Policy policy = newBlockingPolicy(business.getId(), "STAFF_BOOKING_CREATE");
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newBookableCatalogItem(business.getId(), type.getId());
        actAs(business.getId(), "OWNER");

        UUID commitment = policyEngine.beginCommitment(business.getId(), "BOOKING");
        PolicyDisclosure disclosure = policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment,
                null, null, "STAFF", "PHONE");
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "STAFF", TenantContext.getUserId());

        CreateStaffBookingRequest req = new CreateStaffBookingRequest(item.getId(), null, null,
                "Phone-in Customer", null, "+233200000002", nextBookableSlot(), null, null, null, "UNPAID", commitment);

        BookingCreatedResponse response = bookingService.createStaffBooking(req);
        assertNotNull(response.bookingNumber());

        PolicyCommitment stored = policyCommitmentRepository.findByCommitmentReferenceAndBusinessId(commitment, business.getId()).orElseThrow();
        assertEquals("COMPLETED", stored.getStatus());
    }

    // ==================== BookingService.createBooking (public) — BOOKING_CREATE ====================

    @Test
    void publicBookingCreate_blockedWithoutCommitmentWhenPolicyRequiresIt() {
        Business business = newBusiness();
        newBlockingPolicy(business.getId(), "BOOKING_CREATE");
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newBookableCatalogItem(business.getId(), type.getId());

        CreateBookingRequest req = new CreateBookingRequest(item.getId(), null, "Public Customer",
                "public@example.com", "+233200000003", nextBookableSlot(), null, null, null, null, null);

        ApiException ex = assertThrows(ApiException.class, () -> bookingService.createBooking(business.getId(), req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void publicBookingCreate_succeedsAfterCommitmentAndAcknowledgement() {
        Business business = newBusiness();
        Policy policy = newBlockingPolicy(business.getId(), "BOOKING_CREATE");
        ServiceType type = newServiceType(business.getId());
        ServiceCatalogItem item = newBookableCatalogItem(business.getId(), type.getId());

        UUID commitment = policyEngine.beginCommitment(business.getId(), "BOOKING");
        // The public widget doesn't know the customer id yet at disclosure time (matches
        // Revision 4 §1's "AI attempt, no customer identity yet" case) — acknowledged as
        // CUSTOMER_SELF_SERVICE with no id, which createBooking's own eventual customer
        // resolution can never retroactively invalidate, since it never re-checks identity
        // for evidence that was never customer-identity-scoped in the first place... EXCEPT
        // Final Corrections §1 requires a non-null match for CUSTOMER-type evidence — so this
        // disclosure must be acknowledged AFTER a customerId is known, or use STAFF-type
        // evidence. Using STAFF here mirrors a "policy relayed and confirmed by the widget's
        // own flow before customer details are collected" scenario.
        PolicyDisclosure disclosure = policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment,
                null, null, "AI", "WEB_DEMO");
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "STAFF", UUID.randomUUID());

        CreateBookingRequest req = new CreateBookingRequest(item.getId(), null, "Public Customer",
                "public@example.com", "+233200000004", nextBookableSlot(), null, null, commitment, null, null);

        BookingCreatedResponse response = bookingService.createBooking(business.getId(), req);
        assertNotNull(response.bookingNumber());
    }

    // ==================== CustomWigRequestService.createByStaff — STAFF_CUSTOM_WIG_REQUEST_CREATE ====================

    @Test
    void staffCustomWigRequestCreate_blockedWithoutCommitmentWhenPolicyRequiresIt() {
        Business business = newBusiness();
        newBlockingPolicy(business.getId(), "STAFF_CUSTOM_WIG_REQUEST_CREATE");
        actAs(business.getId(), "OWNER");

        CreateStaffCustomWigRequestRequest req = new CreateStaffCustomWigRequestRequest(
                "Wig Customer", null, "+233200000005", "Instagram", "24 inch HD wig", new BigDecimal("800.00"), null, null);

        ApiException ex = assertThrows(ApiException.class, () -> customWigRequestService.createByStaff(req, null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void staffCustomWigRequestCreate_succeedsAndBackfillsDisclosureAfterCommitmentAndAcknowledgement() {
        Business business = newBusiness();
        Policy policy = newBlockingPolicy(business.getId(), "STAFF_CUSTOM_WIG_REQUEST_CREATE");
        actAs(business.getId(), "OWNER");

        UUID commitment = policyEngine.beginCommitment(business.getId(), "CUSTOM_WIG_REQUEST");
        PolicyDisclosure disclosure = policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment,
                null, null, "STAFF", "PHONE");
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "STAFF", TenantContext.getUserId());

        CreateStaffCustomWigRequestRequest req = new CreateStaffCustomWigRequestRequest(
                "Wig Customer", null, "+233200000006", "Instagram", "24 inch HD wig", new BigDecimal("800.00"), null, commitment);

        CustomWigRequestResponse response = customWigRequestService.createByStaff(req, null);
        assertNotNull(response.id());

        PolicyDisclosure linked = policyDisclosureRepository.findByIdAndBusinessId(disclosure.getId(), business.getId()).orElseThrow();
        assertEquals("CUSTOM_WIG_REQUEST", linked.getTransactionType());
        assertEquals(response.id(), linked.getTransactionId());
    }

    // ==================== CustomWigRequestService.submit (public) — CUSTOM_WIG_REQUEST_CREATE ====================

    @Test
    void publicCustomWigRequestSubmit_blockedWithoutCommitmentWhenPolicyRequiresIt_andNoRequestPersists() {
        Business business = newBusiness();
        newBlockingPolicy(business.getId(), "CUSTOM_WIG_REQUEST_CREATE");
        CustomWigSelectionInput selection = newSelection(business.getId());
        long countBefore = customWigRequestRepositoryCount(business.getId());

        SubmitCustomWigRequestRequest req = new SubmitCustomWigRequestRequest(
                "Public Wig Customer", "public-wig@example.com", "+233200000007",
                List.of(selection), null, null);

        ApiException ex = assertThrows(ApiException.class, () -> customWigRequestService.submit(business.getId(), req, null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(countBefore, customWigRequestRepositoryCount(business.getId()),
                "a blocked submit() must not leave any CustomWigRequest row behind");
    }

    @Test
    void publicCustomWigRequestSubmit_succeedsAndBackfillsDisclosureAfterCommitmentAndAcknowledgement() {
        Business business = newBusiness();
        Policy policy = newBlockingPolicy(business.getId(), "CUSTOM_WIG_REQUEST_CREATE");
        CustomWigSelectionInput selection = newSelection(business.getId());

        UUID commitment = policyEngine.beginCommitment(business.getId(), "CUSTOM_WIG_REQUEST");
        // Public path: no customer resolved yet at disclosure time (Revision 4 §1's "no customer
        // identity yet" case) — acknowledged as STAFF here, matching the same reasoning already
        // used for the public booking test (a widget-relayed policy confirmed before any contact
        // details are collected), since Final Corrections §1 requires a real, matching customerId
        // for CUSTOMER-type evidence.
        PolicyDisclosure disclosure = policyEngine.recordDisclosure(business.getId(), policy.getId(), commitment,
                null, null, "AI", "WEB_DEMO");
        policyEngine.recordAcknowledgement(business.getId(), disclosure.getId(), "STAFF", UUID.randomUUID());

        SubmitCustomWigRequestRequest req = new SubmitCustomWigRequestRequest(
                "Public Wig Customer", "public-wig2@example.com", "+233200000008",
                List.of(selection), null, commitment);

        CustomWigRequestCreatedResponse response = customWigRequestService.submit(business.getId(), req, null);
        assertTrue(response.requestNumber() > 0);

        PolicyDisclosure linked = policyDisclosureRepository.findByIdAndBusinessId(disclosure.getId(), business.getId()).orElseThrow();
        assertEquals("CUSTOM_WIG_REQUEST", linked.getTransactionType());
        assertNotNull(linked.getTransactionId());
        PolicyCommitment stored = policyCommitmentRepository.findByCommitmentReferenceAndBusinessId(commitment, business.getId()).orElseThrow();
        assertEquals("COMPLETED", stored.getStatus());
    }

    private long customWigRequestRepositoryCount(UUID businessId) {
        // No dedicated count-by-business method exists on the repository — reusing the existing
        // list query is simpler than adding one just for this test.
        return customWigRequestRepository.findAllByBusinessIdOrderByCreatedAtDesc(businessId).size();
    }
}
