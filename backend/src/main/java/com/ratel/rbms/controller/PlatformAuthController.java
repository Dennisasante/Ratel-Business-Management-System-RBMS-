package com.ratel.rbms.controller;

import com.ratel.rbms.dto.ChangePasswordRequest;
import com.ratel.rbms.dto.ForgotPasswordRequest;
import com.ratel.rbms.dto.PlatformAuthResponse;
import com.ratel.rbms.dto.PlatformLoginRequest;
import com.ratel.rbms.dto.ResetPasswordRequest;
import com.ratel.rbms.service.PlatformAdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/platform/auth")
public class PlatformAuthController {

    private final PlatformAdminService platformAdminService;

    public PlatformAuthController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @PostMapping("/login")
    public PlatformAuthResponse login(@Valid @RequestBody PlatformLoginRequest request) {
        return platformAdminService.login(request);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        platformAdminService.requestReset(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        platformAdminService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UUID adminId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        platformAdminService.changePassword(adminId, request);
        return ResponseEntity.noContent().build();
    }
}
