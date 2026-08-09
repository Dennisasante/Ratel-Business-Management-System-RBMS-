package com.ratel.rbms.repository;

import com.ratel.rbms.entity.ServicePackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServicePackageRepository extends JpaRepository<ServicePackage, UUID> {

    List<ServicePackage> findAllByBusinessIdOrderByNameAsc(UUID businessId);

    Optional<ServicePackage> findByIdAndBusinessId(UUID id, UUID businessId);

    // The public booking picker — only packages the business opted in to online booking for.
    List<ServicePackage> findAllByBusinessIdAndActiveTrueAndBookableOnlineTrueOrderByNameAsc(UUID businessId);
}
