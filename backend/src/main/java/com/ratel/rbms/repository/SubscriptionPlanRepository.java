package com.ratel.rbms.repository;

import com.ratel.rbms.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    List<SubscriptionPlan> findAllByOrderBySortOrderAsc();

    List<SubscriptionPlan> findAllByActiveTrueOrderBySortOrderAsc();
}
