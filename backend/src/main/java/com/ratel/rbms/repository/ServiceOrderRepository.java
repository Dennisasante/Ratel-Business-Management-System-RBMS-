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

    // Backs the list page's type/status filter chips. Every param is optional —
    // pass null to not filter on it, same convention as ActivityLogRepository.search.
    @Query("SELECT so FROM ServiceOrder so WHERE so.businessId = :businessId AND "
            + "(:serviceTypeId IS NULL OR so.serviceTypeId = :serviceTypeId) AND "
            + "(:status IS NULL OR so.status = :status) "
            + "ORDER BY so.receivedAt DESC")
    List<ServiceOrder> search(
            @Param("businessId") UUID businessId,
            @Param("serviceTypeId") UUID serviceTypeId,
            @Param("status") com.ratel.rbms.entity.enums.ServiceOrderStatus status,
            Pageable pageable
    );

    // Report: which orders were received in the window, regardless of where they stand now.
    List<ServiceOrder> findAllByBusinessIdAndReceivedAtBetween(UUID businessId, Instant from, Instant to);

    // Report: which orders were picked up (collected) in the window — revenue and
    // turnaround are both scoped to pickup date, not receipt date.
    List<ServiceOrder> findAllByBusinessIdAndPickedUpAtBetween(UUID businessId, Instant from, Instant to);

    long countByBusinessIdAndServiceTypeId(UUID businessId, UUID serviceTypeId);
}
