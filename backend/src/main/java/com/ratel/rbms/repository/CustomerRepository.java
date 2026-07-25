package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findAllByBusinessIdOrderByFullNameAsc(UUID businessId);

    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);

    // Used by the daily digest to report "N new customers" for the day.
    List<Customer> findAllByBusinessIdAndCreatedAtBetween(UUID businessId, Instant from, Instant to);
}
