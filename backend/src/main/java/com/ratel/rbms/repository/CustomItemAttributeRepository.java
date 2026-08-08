package com.ratel.rbms.repository;

import com.ratel.rbms.entity.CustomItemAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomItemAttributeRepository extends JpaRepository<CustomItemAttribute, UUID> {

    List<CustomItemAttribute> findAllByBusinessIdOrderBySortOrderAsc(UUID businessId);

    Optional<CustomItemAttribute> findByIdAndBusinessId(UUID id, UUID businessId);
}
