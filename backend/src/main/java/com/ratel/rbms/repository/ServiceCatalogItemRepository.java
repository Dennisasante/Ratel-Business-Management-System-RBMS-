package com.ratel.rbms.repository;

import com.ratel.rbms.entity.ServiceCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceCatalogItemRepository extends JpaRepository<ServiceCatalogItem, UUID> {

    List<ServiceCatalogItem> findAllByBusinessIdOrderByNameAsc(UUID businessId);

    Optional<ServiceCatalogItem> findByIdAndBusinessId(UUID id, UUID businessId);

    long countByBusinessIdAndServiceTypeId(UUID businessId, UUID serviceTypeId);

    // The public booking widget's service picker — only ever shows what the
    // business has explicitly opted in to online booking for.
    List<ServiceCatalogItem> findAllByBusinessIdAndActiveTrueAndBookableOnlineTrueOrderByNameAsc(UUID businessId);

    // Cheap "is booking usable at all" check for the /start hub — avoids
    // loading the full list just to see if it's non-empty.
    boolean existsByBusinessIdAndActiveTrueAndBookableOnlineTrue(UUID businessId);
}
