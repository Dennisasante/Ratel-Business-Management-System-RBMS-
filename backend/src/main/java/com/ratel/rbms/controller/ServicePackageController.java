package com.ratel.rbms.controller;

import com.ratel.rbms.dto.ServicePackageRequest;
import com.ratel.rbms.dto.ServicePackageResponse;
import com.ratel.rbms.dto.SetActiveRequest;
import com.ratel.rbms.service.ServicePackageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/service-packages")
public class ServicePackageController {

    private final ServicePackageService servicePackageService;

    public ServicePackageController(ServicePackageService servicePackageService) {
        this.servicePackageService = servicePackageService;
    }

    @GetMapping
    public List<ServicePackageResponse> list() {
        return servicePackageService.list();
    }

    @PostMapping
    public ResponseEntity<ServicePackageResponse> create(@Valid @RequestBody ServicePackageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicePackageService.create(request));
    }

    @PutMapping("/{id}")
    public ServicePackageResponse update(@PathVariable UUID id, @Valid @RequestBody ServicePackageRequest request) {
        return servicePackageService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public ServicePackageResponse setActive(@PathVariable UUID id, @Valid @RequestBody SetActiveRequest request) {
        return servicePackageService.setActive(id, request.active());
    }
}
