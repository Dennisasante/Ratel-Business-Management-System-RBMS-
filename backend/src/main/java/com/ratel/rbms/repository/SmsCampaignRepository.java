package com.ratel.rbms.repository;

import com.ratel.rbms.entity.SmsCampaign;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SmsCampaignRepository extends JpaRepository<SmsCampaign, UUID> {

    Optional<SmsCampaign> findByBusinessIdAndRequestKey(UUID businessId, UUID requestKey);

    Optional<SmsCampaign> findByIdAndBusinessId(UUID id, UUID businessId);

    List<SmsCampaign> findTop50ByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    List<SmsCampaign> findAllByStatus(String status);

    // Settling a campaign (refunds, finishing) takes the campaign's row lock, so two settlers can never both refund.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SmsCampaign c WHERE c.id = :id")
    Optional<SmsCampaign> findByIdForUpdate(@Param("id") UUID id);
}
