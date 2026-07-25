package com.ratel.rbms.controller;

import com.ratel.rbms.dto.AuthResponse;
import com.ratel.rbms.dto.ChangePasswordRequest;
import com.ratel.rbms.dto.ForgotPasswordRequest;
import com.ratel.rbms.dto.GoogleLoginRequest;
import com.ratel.rbms.dto.GoogleRegisterRequest;
import com.ratel.rbms.dto.LoginRequest;
import com.ratel.rbms.dto.RegisterBusinessRequest;
import com.ratel.rbms.dto.ResetPasswordRequest;
import com.ratel.rbms.service.AuthService;
import com.ratel.rbms.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterBusinessRequest request) {
        AuthResponse response = authService.registerBusiness(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/google/register")
    public ResponseEntity<AuthResponse> registerWithGoogle(@Valid @RequestBody GoogleRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerBusinessWithGoogle(request));
    }

    @PostMapping("/google/login")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}
