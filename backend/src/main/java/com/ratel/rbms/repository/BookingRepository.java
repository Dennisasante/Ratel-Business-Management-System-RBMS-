package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    Optional<Booking> findByManageToken(String manageToken);

    // Idempotency lookup, same pattern as SubscriptionPaymentRepository.
    Optional<Booking> findByPaystackReference(String paystackReference);

    Optional<Booking> findByServiceOrderId(UUID serviceOrderId);

    // Property name is "test" (Lombok generates isTest() for the boolean
    // field `test`, and Spring Data resolves the property from that as "test").
    long countByBusinessIdAndTest(UUID businessId, boolean test);

    // Platform-wide total (super admin stats) — non-test bookings across every business.
    long countByTest(boolean test);

    List<Booking> findAllByBusinessIdAndTest(UUID businessId, boolean test);
}
