package com.ratel.rbms.controller;

import com.ratel.rbms.dto.SubscriptionPlanRequest;
import com.ratel.rbms.dto.SubscriptionPlanResponse;
import com.ratel.rbms.service.PlatformSubscriptionPlanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform/subscription-plans")
public class PlatformSubscriptionPlanController {

    private final PlatformSubscriptionPlanService platformSubscriptionPlanService;

    public PlatformSubscriptionPlanController(PlatformSubscriptionPlanService platformSubscriptionPlanService) {
        this.platformSubscriptionPlanService = platformSubscriptionPlanService;
    }

    @GetMapping
    public List<SubscriptionPlanResponse> list() {
        return platformSubscriptionPlanService.listAll();
    }

    @PostMapping
    public SubscriptionPlanResponse create(@Valid @RequestBody SubscriptionPlanRequest request) {
        return platformSubscriptionPlanService.create(request);
    }

    @PutMapping("/{id}")
    public SubscriptionPlanResponse update(@PathVariable UUID id, @Valid @RequestBody SubscriptionPlanRequest request) {
        return platformSubscriptionPlanService.update(id, request);
    }

    @PatchMapping("/{id}/archive")
    public SubscriptionPlanResponse archive(@PathVariable UUID id) {
        return platformSubscriptionPlanService.archive(id);
    }

    @PatchMapping("/{id}/restore")
    public SubscriptionPlanResponse restore(@PathVariable UUID id) {
        return platformSubscriptionPlanService.restore(id);
    }
}
