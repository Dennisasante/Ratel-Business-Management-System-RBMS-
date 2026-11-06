package com.ratel.rbms.controller;

import com.ratel.rbms.dto.PublicLoyaltyBusinessResponse;
import com.ratel.rbms.dto.PublicLoyaltyLookupRequest;
import com.ratel.rbms.dto.PublicLoyaltyResponse;
import com.ratel.rbms.security.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final RateLimiterService rateLimiter;

    public PublicLoyaltyController(LoyaltyService loyaltyService, RateLimiterService rateLimiter) {
        this.loyaltyService = loyaltyService;
        this.rateLimiter = rateLimiter;
    }

    // The business's own points page: a customer types their phone number. Throttled, because a phone
    // number is the only thing standing between a stranger and someone's name and points.
    @GetMapping("/business/{slug}")
    public PublicLoyaltyBusinessResponse business(@PathVariable String slug) {
        return loyaltyService.publicBusiness(slug);
    }

    @PostMapping("/business/{slug}/lookup")
    public PublicLoyaltyResponse lookup(@PathVariable String slug, @Valid @RequestBody PublicLoyaltyLookupRequest request,
                                        HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : http.getRemoteAddr();
        String phoneKey = "loyalty-lookup:" + slug + ":" + request.phone().replaceAll("[^0-9]", "");
        rateLimiter.checkAllowed("loyalty-lookup-ip:" + ip, 30, Duration.ofMinutes(10));
        rateLimiter.checkAllowed(phoneKey, 8, Duration.ofMinutes(10));
        rateLimiter.recordAttempt("loyalty-lookup-ip:" + ip);
        rateLimiter.recordAttempt(phoneKey);
        return loyaltyService.lookupByPhone(slug, request.phone());
    }

    @GetMapping("/{token}")
    public PublicLoyaltyResponse page(@PathVariable String token) {
        return loyaltyService.publicPage(token);
    }
}
