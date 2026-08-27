package com.ratel.rbms.controller;

import com.ratel.rbms.dto.WhatsAppBindingCreateRequest;
import com.ratel.rbms.dto.WhatsAppBindingResponse;
import com.ratel.rbms.dto.WhatsAppBindingUpdateRequest;
import com.ratel.rbms.dto.WhatsAppConnectionTestResponse;
import com.ratel.rbms.service.WhatsAppBindingService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Super Admin only — enforced the same way every other /api/platform/**
 * route is (see SecurityConfig: .requestMatchers("/api/platform/**").hasRole("SUPER_ADMIN")).
 * businessId is a path variable, never accepted as a free-form value from
 * the request body — this is the "Super Admin endpoint may explicitly
 * accept a business ID because platform authentication is intentionally
 * not tenant-scoped" case the spec (§6) describes. No Meta OAuth/onboarding
 * flow exists — this is strictly a developer/admin-configured connection.
 */
@RestController
@RequestMapping("/api/platform/businesses/{businessId}/whatsapp-binding")
public class PlatformWhatsAppBindingController {

    private final WhatsAppBindingService whatsAppBindingService;

    public PlatformWhatsAppBindingController(WhatsAppBindingService whatsAppBindingService) {
        this.whatsAppBindingService = whatsAppBindingService;
    }

    @GetMapping
    public WhatsAppBindingResponse get(@PathVariable UUID businessId) {
        return whatsAppBindingService.get(businessId);
    }

    @PostMapping
    public WhatsAppBindingResponse create(@PathVariable UUID businessId, @Valid @RequestBody WhatsAppBindingCreateRequest request) {
        return whatsAppBindingService.create(currentAdminId(), businessId, request);
    }

    @PutMapping
    public WhatsAppBindingResponse update(@PathVariable UUID businessId, @RequestBody WhatsAppBindingUpdateRequest request) {
        return whatsAppBindingService.update(currentAdminId(), businessId, request);
    }

    @PatchMapping("/active")
    public WhatsAppBindingResponse setActive(@PathVariable UUID businessId, @RequestBody SetActiveRequest request) {
        return whatsAppBindingService.setActive(currentAdminId(), businessId, request.active());
    }

    @PostMapping("/test-connection")
    public WhatsAppConnectionTestResponse testConnection(@PathVariable UUID businessId) {
        return whatsAppBindingService.testConnection(businessId);
    }

    public record SetActiveRequest(boolean active) {
    }

    private UUID currentAdminId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
