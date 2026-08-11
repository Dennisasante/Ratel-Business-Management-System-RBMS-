package com.ratel.rbms.repository;

import com.ratel.rbms.entity.ServiceOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceOrderItemRepository extends JpaRepository<ServiceOrderItem, UUID> {

    List<ServiceOrderItem> findAllByServiceOrderId(UUID serviceOrderId);

    List<ServiceOrderItem> findAllByServiceOrderIdIn(List<UUID> serviceOrderIds);
}
