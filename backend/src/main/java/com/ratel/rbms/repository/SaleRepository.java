package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    List<Sale> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    // Backs the Sales page's filter row (cashier + date range, either or both
    // optional) — same null-safe-cast convention as PaymentTransactionRepository.search
    // (each occurrence of a named parameter is its own `?` placeholder, so
    // every standalone "IS NULL" needs its own explicit cast for Postgres to
    // infer its type).
    @Query("SELECT s FROM Sale s WHERE s.businessId = :businessId AND "
            + "(s.cashierId = :cashierId OR CAST(:cashierId AS uuid) IS NULL) AND "
            + "(s.createdAt >= :from OR CAST(:from AS timestamp) IS NULL) AND "
            + "(s.createdAt < :to OR CAST(:to AS timestamp) IS NULL) "
            + "ORDER BY s.createdAt DESC")
    List<Sale> search(
            @Param("businessId") UUID businessId,
            @Param("cashierId") UUID cashierId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    Optional<Sale> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<Sale> findByPaystackReferenceAndBusinessId(String paystackReference, UUID businessId);

    // Used to compute a customer's lifetime spend/purchase count on demand
    // rather than maintaining a denormalized running total on Customer.
    List<Sale> findAllByBusinessIdAndCustomerId(UUID businessId, UUID customerId);

    // Used by the reports endpoint for the "N sales today" count — revenue
    // itself comes from PaymentTransactionRepository.sumAmount() instead,
    // which covers every money-collecting entity, not just Sale.
    List<Sale> findAllByBusinessIdAndCreatedAtBetween(UUID businessId, Instant from, Instant to);

    // Per-business usage count (super admin stats).
    long countByBusinessId(UUID businessId);
}
