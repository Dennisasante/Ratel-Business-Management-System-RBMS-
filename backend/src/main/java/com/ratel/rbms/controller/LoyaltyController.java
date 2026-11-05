package com.ratel.rbms.controller;

import com.ratel.rbms.dto.LoyaltyCustomerRowResponse;
import com.ratel.rbms.dto.LoyaltyProgramRequest;
import com.ratel.rbms.dto.LoyaltyProgramResponse;
import com.ratel.rbms.dto.LoyaltyStatusResponse;
import com.ratel.rbms.service.LoyaltyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Setting up the program and browsing every customer's standing is an Owner/Manager job; the
// till (any cashier) needs the one-customer lookup and to mark a reward given.
@RestController
@RequestMapping("/api/loyalty")
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    public LoyaltyController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @GetMapping("/program")
    public LoyaltyProgramResponse program() {
        return loyaltyService.getProgram();
    }

    @PutMapping("/program")
    public LoyaltyProgramResponse updateProgram(@Valid @RequestBody LoyaltyProgramRequest request) {
        return loyaltyService.updateProgram(request);
    }

    @GetMapping("/customers")
    public List<LoyaltyCustomerRowResponse> customers() {
        return loyaltyService.overview();
    }

    @GetMapping("/customers/{customerId}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON')")
    public LoyaltyStatusResponse status(@PathVariable UUID customerId) {
        return loyaltyService.statusForCustomer(customerId);
    }

    @PostMapping("/customers/{customerId}/redeem")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON')")
    public LoyaltyStatusResponse redeem(@PathVariable UUID customerId) {
        return loyaltyService.redeem(customerId);
    }
}
