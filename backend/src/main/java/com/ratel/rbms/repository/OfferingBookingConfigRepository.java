package com.ratel.rbms.repository;

import com.ratel.rbms.entity.OfferingBookingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OfferingBookingConfigRepository extends JpaRepository<OfferingBookingConfig, UUID> {

    // Tenant-scoped — never resolve by offeringId alone, same discipline as every other
    // tenant-scoped lookup in this codebase.
    Optional<OfferingBookingConfig> findByOfferingIdAndBusinessId(UUID offeringId, UUID businessId);
}
