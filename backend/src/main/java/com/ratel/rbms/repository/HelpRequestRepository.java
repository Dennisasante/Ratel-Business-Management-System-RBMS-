package com.ratel.rbms.repository;

import com.ratel.rbms.entity.HelpRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, UUID> {

    List<HelpRequest> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    Optional<HelpRequest> findByIdAndBusinessId(UUID id, UUID businessId);

    List<HelpRequest> findAllByOrderByCreatedAtDesc();
}
