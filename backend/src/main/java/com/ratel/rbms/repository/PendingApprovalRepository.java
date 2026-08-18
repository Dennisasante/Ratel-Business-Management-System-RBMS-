package com.ratel.rbms.repository;

import com.ratel.rbms.entity.PendingApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingApprovalRepository extends JpaRepository<PendingApproval, UUID> {

    List<PendingApproval> findAllByBusinessIdAndStatusOrderByRequestedAtDesc(UUID businessId, PendingApproval.Status status);

    Optional<PendingApproval> findByIdAndBusinessId(UUID id, UUID businessId);
}
