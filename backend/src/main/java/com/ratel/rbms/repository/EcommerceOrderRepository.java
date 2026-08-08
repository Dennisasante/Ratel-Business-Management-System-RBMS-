package com.ratel.rbms.repository;

import com.ratel.rbms.entity.EcommerceOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EcommerceOrderRepository extends JpaRepository<EcommerceOrder, UUID> {

    List<EcommerceOrder> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    Optional<EcommerceOrder> findByIdAndBusinessId(UUID id, UUID businessId);

    // Idempotency for webhook retries — Woo may redeliver the same order.created event.
    Optional<EcommerceOrder> findByBusinessIdAndWooOrderId(UUID businessId, long wooOrderId);
}
