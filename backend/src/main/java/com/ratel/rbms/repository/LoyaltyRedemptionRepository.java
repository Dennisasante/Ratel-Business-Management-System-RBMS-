package com.ratel.rbms.repository;

import com.ratel.rbms.entity.LoyaltyRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoyaltyRedemptionRepository extends JpaRepository<LoyaltyRedemption, UUID> {

    List<LoyaltyRedemption> findTop10ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(UUID businessId, UUID customerId);
}
