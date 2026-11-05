package com.ratel.rbms.repository;

import com.ratel.rbms.entity.LoyaltyProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LoyaltyProgramRepository extends JpaRepository<LoyaltyProgram, UUID> {

    // Race-safe lazy creation with the default settings.
    @Modifying
    @Query(value = "INSERT INTO loyalty_programs (business_id) VALUES (:businessId) ON CONFLICT (business_id) DO NOTHING",
            nativeQuery = true)
    void createIfMissing(@Param("businessId") UUID businessId);
}
