package com.ratel.rbms.service;

import com.ratel.rbms.dto.ChangePasswordRequest;
import com.ratel.rbms.dto.CreatePlatformAdminRequest;
import com.ratel.rbms.dto.ForgotPasswordRequest;
import com.ratel.rbms.dto.PlatformAdminSummaryResponse;
import com.ratel.rbms.dto.PlatformAuthResponse;
import com.ratel.rbms.dto.PlatformLoginRequest;
import com.ratel.rbms.dto.ResetPasswordRequest;
import com.ratel.rbms.entity.PlatformAdmin;
import com.ratel.rbms.entity.PlatformAdminResetToken;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.PlatformAdminRepository;
import com.ratel.rbms.repository.PlatformAdminResetTokenRepository;
import com.ratel.rbms.security.JwtService;
import com.ratel.rbms.security.RateLimiterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class PlatformAdminService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PlatformAdminRepository platformAdminRepository;
    private final PlatformAdminResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final RateLimiterService rateLimiterService;
    private final String frontendUrl;

    public PlatformAdminService(
            PlatformAdminRepository platformAdminRepository,
            PlatformAdminResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailService emailService,
            RateLimiterService rateLimiterService,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.platformAdminRepository = platformAdminRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.rateLimiterService = rateLimiterService;
        this.frontendUrl = frontendUrl;
    }

    public PlatformAuthResponse login(PlatformLoginRequest req) {
        String key = "platform-login:" + req.email().toLowerCase();
        rateLimiterService.checkAllowed(key, 5, Duration.ofMinutes(15));

        PlatformAdmin admin = platformAdminRepository.findByEmail(req.email())
                .orElseThrow(() -> {
                    rateLimiterService.recordAttempt(key);
                    return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
                });

        if (!passwordEncoder.matches(req.password(), admin.getPasswordHash())) {
            rateLimiterService.recordAttempt(key);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        rateLimiterService.reset(key);
        String token = jwtService.generatePlatformAdminToken(admin.getId());
        return new PlatformAuthResponse(token, admin.getId(), admin.getFullName(), admin.getEmail(), admin.isMustChangePassword());
    }

    /** An existing Super Admin creates another — same pattern as staff creation, no public route. */
    public PlatformAdminSummaryResponse createAdmin(CreatePlatformAdminRequest req) {
        if (platformAdminRepository.existsByEmail(req.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "An admin account with this email already exists.");
        }

        PlatformAdmin admin = PlatformAdmin.builder()
                .email(req.email())
                .fullName(req.fullName())
                .passwordHash(passwordEncoder.encode(req.temporaryPassword()))
                .mustChangePassword(true)
                .build();
        admin = platformAdminRepository.save(admin);

        return toSummary(admin);
    }

    public List<PlatformAdminSummaryResponse> listAdmins() {
        return platformAdminRepository.findAll().stream().map(this::toSummary).toList();
    }

    public void changePassword(UUID adminId, ChangePasswordRequest req) {
        PlatformAdmin admin = platformAdminRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));

        if (!passwordEncoder.matches(req.currentPassword(), admin.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect.");
        }

        admin.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        admin.setMustChangePassword(false);
        platformAdminRepository.save(admin);
    }

    public void requestReset(ForgotPasswordRequest req) {
        String key = "platform-password-reset:" + req.email().toLowerCase();
        rateLimiterService.checkAllowed(key, 3, Duration.ofHours(1));
        rateLimiterService.recordAttempt(key);

        platformAdminRepository.findByEmail(req.email()).ifPresent(admin -> {
            String token = generateToken();
            PlatformAdminResetToken resetToken = PlatformAdminResetToken.builder()
                    .adminId(admin.getId())
                    .token(token)
                    .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                    .build();
            tokenRepository.save(resetToken);

            String link = frontendUrl + "/platform/reset-password?token=" + token;
            emailService.sendPasswordReset(admin.getEmail(), link);
        });
    }

    public void resetPassword(ResetPasswordRequest req) {
        PlatformAdminResetToken token = tokenRepository.findByToken(req.token())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This reset link is invalid or has already been used."));

        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This reset link has expired. Request a new one.");
        }

        PlatformAdmin admin = platformAdminRepository.findById(token.getAdminId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));

        admin.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        admin.setMustChangePassword(false);
        platformAdminRepository.save(admin);

        token.setUsed(true);
        tokenRepository.save(token);
    }

    private PlatformAdminSummaryResponse toSummary(PlatformAdmin admin) {
        return new PlatformAdminSummaryResponse(admin.getId(), admin.getFullName(), admin.getEmail(), admin.getCreatedAt());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
