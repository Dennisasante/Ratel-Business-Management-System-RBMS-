package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    List<Supplier> findAllByBusinessIdOrderByNameAsc(UUID businessId);

    Optional<Supplier> findByIdAndBusinessId(UUID id, UUID businessId);
}
