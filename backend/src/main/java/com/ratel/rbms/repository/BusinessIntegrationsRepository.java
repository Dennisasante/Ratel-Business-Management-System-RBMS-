package com.ratel.rbms.repository;

import com.ratel.rbms.entity.BusinessIntegrations;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessIntegrationsRepository extends JpaRepository<BusinessIntegrations, UUID> {

    Optional<BusinessIntegrations> findByBusinessId(UUID businessId);
}
