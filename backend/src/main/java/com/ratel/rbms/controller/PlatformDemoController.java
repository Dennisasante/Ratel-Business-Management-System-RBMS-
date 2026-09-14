package com.ratel.rbms.controller;

import com.ratel.rbms.dto.DemoSeedResponse;
import com.ratel.rbms.service.CafeBarNoirDemoSeedService;
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
    private final CafeBarNoirDemoSeedService cafeBarNoirDemoSeedService;

    @Value("${app.demo.seed-enabled}")
    private boolean demoSeedEnabled;

    public PlatformDemoController(DemoSeedService demoSeedService, CafeBarNoirDemoSeedService cafeBarNoirDemoSeedService) {
        this.demoSeedService = demoSeedService;
        this.cafeBarNoirDemoSeedService = cafeBarNoirDemoSeedService;
    }

    @PostMapping("/seed-resort")
    public DemoSeedResponse seedResort() {
        return demoSeedService.seedParadiseBeachResort(demoSeedEnabled);
    }

    // Restaurant-AI-demo phase — the dedicated Cafe Bar Noir tenant used to demo package
    // customisation + policy-aware booking. Same two independent gates as seedResort() above.
    @PostMapping("/seed-cafe-bar-noir")
    public DemoSeedResponse seedCafeBarNoir() {
        return cafeBarNoirDemoSeedService.seedCafeBarNoir(demoSeedEnabled);
    }
}
