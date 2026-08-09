package com.ratel.rbms.controller;

import com.ratel.rbms.dto.StartHubConfigResponse;
import com.ratel.rbms.service.PublicStartService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No JWT — this is the one link a business puts in its WhatsApp bio, same
 * reasoning as PublicBookingController/PublicCustomWigController.
 */
@RestController
@RequestMapping("/api/public/start")
public class PublicStartController {

    private final PublicStartService publicStartService;

    public PublicStartController(PublicStartService publicStartService) {
        this.publicStartService = publicStartService;
    }

    @GetMapping("/by-slug/{slug}")
    public StartHubConfigResponse configBySlug(@PathVariable String slug) {
        return publicStartService.getConfigBySlug(slug);
    }
}
