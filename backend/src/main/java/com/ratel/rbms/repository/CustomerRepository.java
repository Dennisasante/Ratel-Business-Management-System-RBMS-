package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findAllByBusinessIdOrderByFullNameAsc(UUID businessId);

    // Backs the customers page search box. :search is cast explicitly — otherwise
    // Postgres can't infer a type for the bind parameter when it's null (the
    // "no filter" case) and LOWER() fails with "function lower(bytea) does not
    // exist" — same gotcha ProductRepository.search() already documents.
    @Query("SELECT c FROM Customer c WHERE c.businessId = :businessId AND "
            + "(:search IS NULL OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) "
            + "OR LOWER(c.phone) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) "
            + "OR LOWER(c.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) "
            + "ORDER BY c.fullName ASC")
    List<Customer> search(@Param("businessId") UUID businessId, @Param("search") String search);

    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);

    // Used by the daily digest to report "N new customers" for the day.
    List<Customer> findAllByBusinessIdAndCreatedAtBetween(UUID businessId, Instant from, Instant to);

    // Matches a returning booking-widget customer by phone/WhatsApp so repeat
    // bookings link to the same customer record instead of creating duplicates.
    Optional<Customer> findFirstByBusinessIdAndPhone(UUID businessId, String phone);

    // Per-business usage count (super admin stats).
    long countByBusinessId(UUID businessId);
}
