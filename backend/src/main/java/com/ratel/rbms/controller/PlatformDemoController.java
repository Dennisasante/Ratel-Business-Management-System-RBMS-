package com.ratel.rbms.controller;

import com.ratel.rbms.dto.DemoSeedResponse;
import com.ratel.rbms.service.DemoSeedService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo-data provisioning for the Tallia AI sales demo — Super-Admin-only
 * (inherits the blanket "/api/platform/**" -> hasRole('SUPER_ADMIN') rule
 * in SecurityConfig, same as every other platform endpoint) AND gated
 * behind app.demo.seed-enabled, which defaults false everywhere including
 * production. Both gates are independent; neither implies the other.
 */
@RestController
@RequestMapping("/api/platform/demo")
public class PlatformDemoController {

    private final DemoSeedService demoSeedService;

    @Value("${app.demo.seed-enabled}")
    private boolean demoSeedEnabled;

    public PlatformDemoController(DemoSeedService demoSeedService) {
        this.demoSeedService = demoSeedService;
    }

    @PostMapping("/seed-resort")
    public DemoSeedResponse seedResort() {
        return demoSeedService.seedParadiseBeachResort(demoSeedEnabled);
    }
}
