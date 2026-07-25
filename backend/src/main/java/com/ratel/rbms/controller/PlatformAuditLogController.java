package com.ratel.rbms.controller;

import com.ratel.rbms.dto.PlatformAuditLogResponse;
import com.ratel.rbms.service.PlatformAuditLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/audit-logs")
public class PlatformAuditLogController {

    private final PlatformAuditLogService platformAuditLogService;

    public PlatformAuditLogController(PlatformAuditLogService platformAuditLogService) {
        this.platformAuditLogService = platformAuditLogService;
    }

    @GetMapping
    public List<PlatformAuditLogResponse> list() {
        return platformAuditLogService.listAll();
    }
}
