package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
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

    // Per-business usage count (super admin stats).
    long countByBusinessId(UUID businessId);

    // Platform-wide revenue total (super admin stats) — a real SUM query
    // instead of loading every sale ever made across every business.
    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s")
    BigDecimal sumTotalAmount();
}
