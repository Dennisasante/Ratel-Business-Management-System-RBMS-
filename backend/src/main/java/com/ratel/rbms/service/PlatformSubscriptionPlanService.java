package com.ratel.rbms.service;

import com.ratel.rbms.dto.SubscriptionPlanRequest;
import com.ratel.rbms.dto.SubscriptionPlanResponse;
import com.ratel.rbms.entity.SubscriptionPlan;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.SubscriptionPlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PlatformSubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public PlatformSubscriptionPlanService(SubscriptionPlanRepository subscriptionPlanRepository) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    // Includes archived plans, unlike BillingService.listPlans() — the super
    // admin needs to see (and be able to restore) retired tiers too.
    public List<SubscriptionPlanResponse> listAll() {
        return subscriptionPlanRepository.findAll().stream()
                .sorted(Comparator.comparingInt(SubscriptionPlan::getSortOrder))
                .map(SubscriptionPlanResponse::from)
                .toList();
    }

    public SubscriptionPlanResponse create(SubscriptionPlanRequest req) {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(req.name())
                .price(req.price())
                .currency(req.currency())
                .billingPeriodDays(req.billingPeriodDays())
                .sortOrder(req.sortOrder())
                .build();
        return SubscriptionPlanResponse.from(subscriptionPlanRepository.save(plan));
    }

    public SubscriptionPlanResponse update(UUID id, SubscriptionPlanRequest req) {
        SubscriptionPlan plan = getOrThrow(id);
        plan.setName(req.name());
        plan.setPrice(req.price());
        plan.setCurrency(req.currency());
        plan.setBillingPeriodDays(req.billingPeriodDays());
        plan.setSortOrder(req.sortOrder());
        return SubscriptionPlanResponse.from(subscriptionPlanRepository.save(plan));
    }

    // Archive, never hard delete — same reasoning as products: subscription_payments
    // already references this plan by id for historical records, even ones from
    // long-since-cancelled businesses.
    public SubscriptionPlanResponse archive(UUID id) {
        SubscriptionPlan plan = getOrThrow(id);
        plan.setActive(false);
        return SubscriptionPlanResponse.from(subscriptionPlanRepository.save(plan));
    }

    public SubscriptionPlanResponse restore(UUID id) {
        SubscriptionPlan plan = getOrThrow(id);
        plan.setActive(true);
        return SubscriptionPlanResponse.from(subscriptionPlanRepository.save(plan));
    }

    private SubscriptionPlan getOrThrow(UUID id) {
        return subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Plan not found."));
    }
}
