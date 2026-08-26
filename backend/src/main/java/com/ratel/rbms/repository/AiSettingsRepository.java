package com.ratel.rbms.repository;

import com.ratel.rbms.entity.AiSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiSettingsRepository extends JpaRepository<AiSettings, UUID> {

    Optional<AiSettings> findByBusinessId(UUID businessId);
}
