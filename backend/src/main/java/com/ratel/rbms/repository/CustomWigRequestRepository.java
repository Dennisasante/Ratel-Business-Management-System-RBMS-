package com.ratel.rbms.repository;

import com.ratel.rbms.entity.CustomWigRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomWigRequestRepository extends JpaRepository<CustomWigRequest, UUID> {

    List<CustomWigRequest> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    Optional<CustomWigRequest> findByIdAndBusinessId(UUID id, UUID businessId);
}
