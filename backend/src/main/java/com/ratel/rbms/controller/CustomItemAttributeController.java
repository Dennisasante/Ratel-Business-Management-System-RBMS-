package com.ratel.rbms.controller;

import com.ratel.rbms.dto.CustomItemAttributeRequest;
import com.ratel.rbms.dto.CustomItemAttributeResponse;
import com.ratel.rbms.service.CustomItemAttributeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/custom-wig-attributes")
public class CustomItemAttributeController {

    private final CustomItemAttributeService customItemAttributeService;

    public CustomItemAttributeController(CustomItemAttributeService customItemAttributeService) {
        this.customItemAttributeService = customItemAttributeService;
    }

    @GetMapping
    public List<CustomItemAttributeResponse> list() {
        return customItemAttributeService.list();
    }

    @PostMapping
    public ResponseEntity<CustomItemAttributeResponse> create(@Valid @RequestBody CustomItemAttributeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customItemAttributeService.create(request));
    }

    @PutMapping("/{id}")
    public CustomItemAttributeResponse update(@PathVariable UUID id, @Valid @RequestBody CustomItemAttributeRequest request) {
        return customItemAttributeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customItemAttributeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
