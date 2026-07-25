package com.ratel.rbms.repository;

import com.ratel.rbms.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    List<PurchaseOrder> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    Optional<PurchaseOrder> findByIdAndBusinessId(UUID id, UUID businessId);
}
