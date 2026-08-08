package com.ratel.rbms.repository;

import com.ratel.rbms.entity.EcommerceOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EcommerceOrderItemRepository extends JpaRepository<EcommerceOrderItem, UUID> {

    List<EcommerceOrderItem> findAllByEcommerceOrderId(UUID ecommerceOrderId);

    // An order.updated webhook doesn't give us a diff of line items, so the
    // simplest correct approach is to clear and rebuild them from the fresh
    // payload every time — see WooCommerceSyncService.
    void deleteAllByEcommerceOrderId(UUID ecommerceOrderId);
}
