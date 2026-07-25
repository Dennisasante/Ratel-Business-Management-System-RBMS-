package com.ratel.rbms.controller;

import com.ratel.rbms.dto.ServiceTypeRequest;
import com.ratel.rbms.dto.ServiceTypeResponse;
import com.ratel.rbms.service.ServiceTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/service-types")
public class ServiceTypeController {

    private final ServiceTypeService serviceTypeService;

    public ServiceTypeController(ServiceTypeService serviceTypeService) {
        this.serviceTypeService = serviceTypeService;
    }

    @GetMapping
    public List<ServiceTypeResponse> list() {
        return serviceTypeService.listAll();
    }

    @PostMapping
    public ResponseEntity<ServiceTypeResponse> create(@Valid @RequestBody ServiceTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceTypeService.create(request));
    }

    @PutMapping("/{id}")
    public ServiceTypeResponse rename(@PathVariable UUID id, @Valid @RequestBody ServiceTypeRequest request) {
        return serviceTypeService.rename(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        serviceTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
