package com.ratel.rbms.repository;

import com.ratel.rbms.entity.PlatformBillingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// Single-row table (see migration V11) — callers should always treat
// findAll().get(0) / findFirstBy...() as "the one settings row".
public interface PlatformBillingSettingsRepository extends JpaRepository<PlatformBillingSettings, UUID> {

    PlatformBillingSettings findFirstByOrderByUpdatedAtDesc();
}
