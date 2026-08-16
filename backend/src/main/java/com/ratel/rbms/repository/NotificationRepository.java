package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop50ByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    Optional<Notification> findByIdAndBusinessId(UUID id, UUID businessId);

    long countByBusinessIdAndReadFalse(UUID businessId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.businessId = :businessId AND n.read = false")
    void markAllRead(@Param("businessId") UUID businessId);
}
