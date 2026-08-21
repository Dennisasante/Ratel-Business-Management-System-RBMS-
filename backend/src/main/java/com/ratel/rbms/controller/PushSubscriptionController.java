package com.ratel.rbms.controller;

import com.ratel.rbms.dto.PushSubscribeRequest;
import com.ratel.rbms.service.PushSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/push-subscriptions")
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;

    public PushSubscriptionController(PushSubscriptionService pushSubscriptionService) {
        this.pushSubscriptionService = pushSubscriptionService;
    }

    @PostMapping
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscribeRequest request) {
        pushSubscriptionService.subscribe(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unsubscribe(@RequestParam String endpoint) {
        pushSubscriptionService.unsubscribe(endpoint);
        return ResponseEntity.noContent().build();
    }
}
