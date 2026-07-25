package com.ratel.rbms.controller;

import com.ratel.rbms.dto.BusinessResponse;
import com.ratel.rbms.dto.BusinessUpdateRequest;
import com.ratel.rbms.service.BusinessService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/business")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @GetMapping("/me")
    public BusinessResponse me() {
        return businessService.getMine();
    }

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public BusinessResponse update(@Valid @RequestBody BusinessUpdateRequest request) {
        return businessService.updateProfile(request);
    }

    @PostMapping("/logo")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public BusinessResponse uploadLogo(@RequestParam("file") MultipartFile file) {
        return businessService.uploadLogo(file);
    }
}
