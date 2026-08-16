package com.ratel.rbms.repository;

import com.ratel.rbms.entity.HelpRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, UUID> {

    List<HelpRequest> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    Optional<HelpRequest> findByIdAndBusinessId(UUID id, UUID businessId);

    List<HelpRequest> findAllByOrderByCreatedAtDesc();

    // Super Admin weekly digest: new requests this week still awaiting a response.
    long countByStatusAndCreatedAtBetween(String status, Instant from, Instant to);
}
