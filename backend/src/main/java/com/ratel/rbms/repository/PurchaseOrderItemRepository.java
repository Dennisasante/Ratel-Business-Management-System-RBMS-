package com.ratel.rbms.repository;

import com.ratel.rbms.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, UUID> {

    List<PurchaseOrderItem> findAllByPurchaseOrderId(UUID purchaseOrderId);
}
