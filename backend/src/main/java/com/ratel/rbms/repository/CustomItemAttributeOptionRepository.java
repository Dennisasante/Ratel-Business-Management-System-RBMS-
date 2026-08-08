package com.ratel.rbms.repository;

import com.ratel.rbms.entity.CustomItemAttributeOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomItemAttributeOptionRepository extends JpaRepository<CustomItemAttributeOption, UUID> {

    List<CustomItemAttributeOption> findAllByAttributeIdOrderBySortOrderAsc(UUID attributeId);

    Optional<CustomItemAttributeOption> findByIdAndAttributeId(UUID id, UUID attributeId);

    void deleteAllByAttributeId(UUID attributeId);
}
