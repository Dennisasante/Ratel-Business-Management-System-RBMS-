package com.ratel.rbms.repository;

import com.ratel.rbms.entity.ActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    // Capped lists rather than full pagination — plenty for V1's "what happened
    // recently" use case without adding pagination plumbing everywhere.
    List<ActivityLog> findTop300ByOrderByCreatedAtDesc();

    List<ActivityLog> findTop300ByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    // Used for the Super Admin's growth chart — deliberately uncapped within
    // the date window, unlike the "top 300 recent" views above.
    List<ActivityLog> findAllByCreatedAtAfter(Instant after);

    // Backs the Activity Log filter controls (by staff member, by date range).
    // Every param is optional — pass null to not filter on it. businessId is
    // also optional so the same query serves both the business-scoped
    // endpoint (always passes its own businessId) and the Super Admin's
    // platform-wide one (passes null to search everyone, or a specific id).
    // Every param is compared once and cast-null-checked once (never a bare
    // repeated ":param IS NULL"): Postgres/Hibernate infers each occurrence of
    // a named parameter's type independently, so a nullable UUID/Instant left
    // untyped on its IS NULL occurrence fails to bind (see the same fix on
    // ProductRepository.search / PaymentTransactionRepository.search).
    @Query("SELECT a FROM ActivityLog a WHERE "
            + "(a.businessId = :businessId OR CAST(:businessId AS uuid) IS NULL) AND "
            + "(a.userId = :userId OR CAST(:userId AS uuid) IS NULL) AND "
            + "(a.createdAt >= :from OR CAST(:from AS timestamp) IS NULL) AND "
            + "(a.createdAt <= :to OR CAST(:to AS timestamp) IS NULL) "
            + "ORDER BY a.createdAt DESC")
    List<ActivityLog> search(
            @Param("businessId") UUID businessId,
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
