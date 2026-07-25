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
    @Query("SELECT a FROM ActivityLog a WHERE "
            + "(:businessId IS NULL OR a.businessId = :businessId) AND "
            + "(:userId IS NULL OR a.userId = :userId) AND "
            + "(:from IS NULL OR a.createdAt >= :from) AND "
            + "(:to IS NULL OR a.createdAt <= :to) "
            + "ORDER BY a.createdAt DESC")
    List<ActivityLog> search(
            @Param("businessId") UUID businessId,
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
