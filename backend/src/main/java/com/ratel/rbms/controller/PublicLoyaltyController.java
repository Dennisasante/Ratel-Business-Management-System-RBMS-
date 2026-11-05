package com.ratel.rbms.controller;

import com.ratel.rbms.dto.PublicLoyaltyResponse;
import com.ratel.rbms.service.LoyaltyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Open to anyone holding the link (like the booking pages): the 10-character token is the credential.
@RestController
@RequestMapping("/api/public/loyalty")
public class PublicLoyaltyController {

    private final LoyaltyService loyaltyService;

    public PublicLoyaltyController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @GetMapping("/{token}")
    public PublicLoyaltyResponse page(@PathVariable String token) {
        return loyaltyService.publicPage(token);
    }
}
