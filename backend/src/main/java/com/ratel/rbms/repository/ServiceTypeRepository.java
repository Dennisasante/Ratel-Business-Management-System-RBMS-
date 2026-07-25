package com.ratel.rbms.repository;

import com.ratel.rbms.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, UUID> {

    List<ServiceType> findAllByBusinessIdOrderByNameAsc(UUID businessId);

    Optional<ServiceType> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);
}
