package com.ratel.rbms.controller;

import com.ratel.rbms.dto.CreatePlatformAdminRequest;
import com.ratel.rbms.dto.PlatformAdminSummaryResponse;
import com.ratel.rbms.service.PlatformAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/admins")
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;

    public PlatformAdminController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping
    public List<PlatformAdminSummaryResponse> list() {
        return platformAdminService.listAdmins();
    }

    @PostMapping
    public ResponseEntity<PlatformAdminSummaryResponse> create(@Valid @RequestBody CreatePlatformAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platformAdminService.createAdmin(request));
    }
}
