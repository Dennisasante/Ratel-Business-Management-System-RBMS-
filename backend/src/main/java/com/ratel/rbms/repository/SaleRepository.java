package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    List<Sale> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    Optional<Sale> findByIdAndBusinessId(UUID id, UUID businessId);

    // Used to compute a customer's lifetime spend/purchase count on demand
    // rather than maintaining a denormalized running total on Customer.
    List<Sale> findAllByBusinessIdAndCustomerId(UUID businessId, UUID customerId);

    // Used by the reports endpoint to total revenue within a date range.
    List<Sale> findAllByBusinessIdAndCreatedAtBetween(UUID businessId, Instant from, Instant to);
}
