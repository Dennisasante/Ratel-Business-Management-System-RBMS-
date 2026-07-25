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
}
