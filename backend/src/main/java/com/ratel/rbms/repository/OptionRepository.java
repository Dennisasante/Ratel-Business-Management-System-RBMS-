package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Option;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OptionRepository extends JpaRepository<Option, UUID> {

    // Tenant-scoped — never resolve an Option by id alone, same discipline as every other
    // tenant-scoped lookup in this codebase.
    Optional<Option> findByIdAndBusinessId(UUID id, UUID businessId);
}
