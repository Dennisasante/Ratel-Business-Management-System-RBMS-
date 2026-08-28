package com.ratel.rbms.repository;

import com.ratel.rbms.entity.ServiceOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, UUID> {

    Optional<ServiceOrder> findByIdAndBusinessId(UUID id, UUID businessId);

    // Backs the Super Admin per-business data-cleanup list.
    List<ServiceOrder> findAllByBusinessIdOrderByReceivedAtDesc(UUID businessId);

    Optional<ServiceOrder> findByPaystackReferenceAndBusinessId(String paystackReference, UUID businessId);

    // Backs the list page's type/status/date filters. Every param is optional —
    // pass null to not filter on it, same convention as ActivityLogRepository.search.
    // :from/:to are Instant — a bare "? IS NULL" occurrence leaves Postgres
    // unable to infer that parameter's type (confirmed: "could not determine
    // data type of parameter"), same root cause as the CAST(...) convention
    // already established on ActivityLogRepository.search/PaymentTransactionRepository.search.
    // serviceTypeId/status don't need it — Hibernate already binds those with
    // an explicit SQL type from the method parameter's own Java type.
    @Query("SELECT so FROM ServiceOrder so WHERE so.businessId = :businessId AND "
            + "(:serviceTypeId IS NULL OR so.serviceTypeId = :serviceTypeId) AND "
            + "(:status IS NULL OR so.status = :status) AND "
            + "(so.receivedAt >= :from OR CAST(:from AS timestamp) IS NULL) AND "
            + "(so.receivedAt < :to OR CAST(:to AS timestamp) IS NULL) "
            + "ORDER BY so.receivedAt DESC")
    List<ServiceOrder> search(
            @Param("businessId") UUID businessId,
            @Param("serviceTypeId") UUID serviceTypeId,
            @Param("status") com.ratel.rbms.entity.enums.ServiceOrderStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    // Report: which orders were received in the window, regardless of where they stand now.
    List<ServiceOrder> findAllByBusinessIdAndReceivedAtBetween(UUID businessId, Instant from, Instant to);

    // Report: which orders were picked up (collected) in the window — revenue and
    // turnaround are both scoped to pickup date, not receipt date.
    List<ServiceOrder> findAllByBusinessIdAndPickedUpAtBetween(UUID businessId, Instant from, Instant to);

    long countByBusinessIdAndServiceTypeId(UUID businessId, UUID serviceTypeId);

    // Per-business usage count (super admin stats). Platform-wide total uses
    // the inherited count().
    long countByBusinessId(UUID businessId);

    // Candidates that could possibly overlap a requested booking window, narrowed
    // by the caller to [requestedStart - duration, requestedEnd) — any wider bound
    // is unnecessary since every order for the same catalog item shares that
    // duration. BookingService does the exact overlap check in Java afterward.
    List<ServiceOrder> findAllByBusinessIdAndServiceCatalogIdAndStatusNotAndScheduledAtBetween(
            UUID businessId, UUID serviceCatalogId, com.ratel.rbms.entity.enums.ServiceOrderStatus status,
            Instant from, Instant to
    );

    // Same overlap-candidate query as above, scoped to a package booking instead.
    List<ServiceOrder> findAllByBusinessIdAndServicePackageIdAndStatusNotAndScheduledAtBetween(
            UUID businessId, UUID servicePackageId, com.ratel.rbms.entity.enums.ServiceOrderStatus status,
            Instant from, Instant to
    );
}
