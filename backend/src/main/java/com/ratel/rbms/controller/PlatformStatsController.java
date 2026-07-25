package com.ratel.rbms.controller;

import com.ratel.rbms.dto.PlatformStatsResponse;
import com.ratel.rbms.service.PlatformStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/stats")
public class PlatformStatsController {

    private final PlatformStatsService platformStatsService;

    public PlatformStatsController(PlatformStatsService platformStatsService) {
        this.platformStatsService = platformStatsService;
    }

    @GetMapping
    public PlatformStatsResponse stats() {
        return platformStatsService.getStats();
    }
}
