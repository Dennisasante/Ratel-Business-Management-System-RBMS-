package com.ratel.rbms.controller;

import com.ratel.rbms.dto.ServiceCatalogItemRequest;
import com.ratel.rbms.dto.ServiceCatalogItemResponse;
import com.ratel.rbms.dto.SetActiveRequest;
import com.ratel.rbms.service.ServiceCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/service-catalog")
public class ServiceCatalogController {

    private final ServiceCatalogService serviceCatalogService;

    public ServiceCatalogController(ServiceCatalogService serviceCatalogService) {
        this.serviceCatalogService = serviceCatalogService;
    }

    @GetMapping
    public List<ServiceCatalogItemResponse> list(@RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        return serviceCatalogService.listAll(activeOnly);
    }

    @PostMapping
    public ResponseEntity<ServiceCatalogItemResponse> create(@Valid @RequestBody ServiceCatalogItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceCatalogService.create(request));
    }

    @PutMapping("/{id}")
    public ServiceCatalogItemResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceCatalogItemRequest request) {
        return serviceCatalogService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public ServiceCatalogItemResponse setActive(@PathVariable UUID id, @Valid @RequestBody SetActiveRequest request) {
        return serviceCatalogService.setActive(id, request.active());
    }
}
