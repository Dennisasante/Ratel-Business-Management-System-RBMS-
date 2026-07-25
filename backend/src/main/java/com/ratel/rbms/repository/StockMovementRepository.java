package com.ratel.rbms.repository;

import com.ratel.rbms.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findAllByProductIdAndBusinessIdOrderByCreatedAtDesc(UUID productId, UUID businessId);
}
