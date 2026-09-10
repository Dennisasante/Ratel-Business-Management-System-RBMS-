package com.ratel.rbms.service;

import com.ratel.rbms.dto.CreateStaffBookingRequest;
import com.ratel.rbms.dto.ServiceCatalogItemRequest;
import com.ratel.rbms.dto.ServiceCatalogItemResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.Policy;
import com.ratel.rbms.entity.PolicyCommitment;
import com.ratel.rbms.entity.PolicyOverride;
import com.ratel.rbms.entity.PolicyVersion;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.PolicyCommitmentRepository;
import com.ratel.rbms.repository.PolicyOverrideRepository;
import com.ratel.rbms.repository.PolicyRepository;
import com.ratel.rbms.repository.PolicyVersionRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceOrderLineSnapshotRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5C — real-PostgreSQL proof that a genuine downstream failure,
 * occurring AFTER canonical pricing succeeds and AFTER {@link PolicyEngine#evaluateGate} has
 * consumed a {@link PolicyOverride} but BEFORE {@code createStaffBooking}'s enclosing transaction
 * commits, leaves nothing behind — no ServiceOrder, no Booking, no snapshot, no completed
 * commitment, no consumed override.
 *
 * <p>The failure is triggered by real, unmodified application code — an {@code assignedStaffId}
 * that doesn't exist violates {@code service_orders.assigned_staff_id}'s own real foreign key
 * (REFERENCES staff_members) at INSERT time, exactly the same real-FK-violation technique
 * {@code Phase4RollbackTest} uses for {@code SaleService}. Deliberately NOT {@code @Transactional}
 * at the test level — same reasoning as {@code Phase4RollbackTest}/{@code Phase4ConcurrencyTest}.
 */
@SpringBootTest
class Phase5CRollbackTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private PolicyVersionRepository policyVersionRepository;
    @Autowired private PolicyCommitmentRepository policyCommitmentRepository;
    @Autowired private PolicyOverrideRepository policyOverrideRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ServiceOrderRepository serviceOrderRepository;
    @Autowired private ServiceOrderLineSnapshotRepository serviceOrderLineSnapshotRepository;
    @Autowired private ServiceCatalogService serviceCatalogService;
    @Autowired private PolicyEngine policyEngine;
    @Autowired private BookingCutoverStateResolver bookingCutoverStateResolver;
    @Autowired private BookingService bookingService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private static Instant nextValidBookingSlot() {
        ZonedDateTime zdt = ZonedDateTime.now(ZoneOffset.UTC).plusDays(3).withHour(11).withMinute(0).withSecond(0).withNano(0);
        while (zdt.getDayOfWeek() == DayOfWeek.SUNDAY) zdt = zdt.plusDays(1);
        return zdt.toInstant();
    }

    @Test
    void downstreamFailureAfterCanonicalPricingAndOverrideConsumptionRollsBackEverything_realDatabaseProof() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Business business = businessRepository.save(Business.builder()
                .name("Phase5C Rollback Test Business " + unique).slug("phase5c-rollback-" + unique)
                .industry(Industry.OTHER).currency("GHS").build());

        Policy policy = policyRepository.save(Policy.builder()
                .businessId(business.getId()).policyKey("rollback-test-" + unique)
                .appliesToAction("STAFF_BOOKING_CREATE").createdBy(UUID.randomUUID()).build());
        PolicyVersion version = policyVersionRepository.save(PolicyVersion.builder()
                .businessId(business.getId()).policyId(policy.getId()).versionNumber(1)
                .title("Required").content("Must be shown and agreed to.")
                .requiresAcknowledgement(true).blocksTransaction(true).staffOverridable(true)
                .createdBy(UUID.randomUUID()).build());

        Customer customer = customerRepository.save(Customer.builder()
                .businessId(business.getId()).fullName("Rollback Test Customer").phone("+233200000098").build());
        User owner = userRepository.save(User.builder()
                .businessId(business.getId()).fullName("Rollback Test Owner")
                .email("phase5c-rollback-owner+" + unique + "@example.com").role(Role.OWNER).build());

        TenantContext.setBusinessId(business.getId());
        TenantContext.setUserId(owner.getId());
        TenantContext.setRole("OWNER");

        ServiceType type = serviceTypeRepository.save(ServiceType.builder().businessId(business.getId()).name("Svc").build());
        ServiceCatalogItemResponse item = serviceCatalogService.create(
                new ServiceCatalogItemRequest(type.getId(), "Rollback Canonical Service", new BigDecimal("60.00"), true, 30, 1, false, null));

        bookingCutoverStateResolver.markVerified(business.getId(), new VerificationResult(business.getId(), List.of(), List.of()));
        bookingCutoverStateResolver.enableCanonical(business.getId());

        UUID commitment = policyEngine.beginCommitment(business.getId()); // unbound, ACTIVE
        UUID overrideId = policyEngine.requestOverride(business.getId(), commitment, policy.getId(), version.getId(),
                owner.getId(), "Rollback proof — owner override.", true); // auto-APPROVED

        UUID bogusStaffId = UUID.randomUUID(); // no such staff_members row exists

        CreateStaffBookingRequest req = new CreateStaffBookingRequest(
                item.id(), null, customer.getId(), null, null, null,
                nextValidBookingSlot(), null, null, bogusStaffId, "UNPAID", commitment);

        // The FK violation surfaces as Spring's own DataIntegrityViolationException (unmapped —
        // no service code catches it), which IS a RuntimeException, so Spring's default
        // rollback-on-RuntimeException still applies exactly as it would for an ApiException
        // (Phase4RollbackTest's own failure point happened to be an explicit ApiException instead;
        // this one is a raw DB constraint violation — a different, equally real downstream
        // failure, both proving the same rollback guarantee). The important proof is what happened
        // to persisted state below, not this specific exception type.
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> bookingService.createStaffBooking(req));

        // ---- Fresh reads, after the real rollback, each its own new Spring Data transaction ----

        PolicyOverride overrideAfter = policyOverrideRepository.findByIdAndBusinessId(overrideId, business.getId()).orElseThrow();
        assertEquals("APPROVED", overrideAfter.getStatus(), "the override must revert to APPROVED, not remain CONSUMED");
        assertNull(overrideAfter.getConsumedAt());

        PolicyCommitment commitmentAfter = policyCommitmentRepository.findByCommitmentReferenceAndBusinessId(commitment, business.getId()).orElseThrow();
        assertEquals("ACTIVE", commitmentAfter.getStatus(), "the commitment must remain ACTIVE, never COMPLETED");
        assertNull(commitmentAfter.getCompletedAt());

        assertTrue(bookingRepository.findAllByBusinessIdOrderByCreatedAtDesc(business.getId()).isEmpty(), "no partial Booking row may survive");
        assertTrue(serviceOrderRepository.findAllByBusinessIdOrderByReceivedAtDesc(business.getId()).isEmpty(), "no partial ServiceOrder row may survive");

        // No snapshot either — it would have to reference a ServiceOrder that itself never
        // committed, so there is nothing to look up by service_order_id at all; the absence of any
        // ServiceOrder above already proves this, restated here as an explicit assertion for the
        // benefit of anyone reading this test's own list of guarantees.
        assertTrue(serviceOrderRepository.findAllByBusinessIdOrderByReceivedAtDesc(business.getId()).stream()
                .flatMap(o -> serviceOrderLineSnapshotRepository.findAllByBusinessIdAndServiceOrderId(business.getId(), o.getId()).stream())
                .findAny().isEmpty(), "no snapshot row may survive either");

        policyOverrideRepository.deleteById(overrideId);
        policyCommitmentRepository.deleteById(commitment);
        // Policy/PolicyVersion/Business left in place — append-only, same established precedent.
    }
}
