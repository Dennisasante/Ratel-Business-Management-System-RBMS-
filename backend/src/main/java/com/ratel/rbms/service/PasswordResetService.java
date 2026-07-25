package com.ratel.rbms.service;

import com.ratel.rbms.dto.ForgotPasswordRequest;
import com.ratel.rbms.dto.ResetPasswordRequest;
import com.ratel.rbms.entity.PasswordResetToken;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.PasswordResetTokenRepository;
import com.ratel.rbms.repository.UserRepository;
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

@Service
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;
    private final RateLimiterService rateLimiterService;
    private final String frontendUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            ActivityLogService activityLogService,
            RateLimiterService rateLimiterService,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.activityLogService = activityLogService;
        this.rateLimiterService = rateLimiterService;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Always looks like it succeeded, whether or not the email belongs to an
     * account — telling the caller "no account with that email" would let
     * someone enumerate registered users one guess at a time.
     */
    public void requestReset(ForgotPasswordRequest req) {
        // Limits per-email regardless of whether the account exists, so this
        // can't be used to spam someone's inbox with reset emails either.
        String key = "password-reset:" + req.email().toLowerCase();
        rateLimiterService.checkAllowed(key, 3, Duration.ofHours(1));
        rateLimiterService.recordAttempt(key);

        userRepository.findByEmail(req.email()).ifPresent(user -> {
            String token = generateToken();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .userId(user.getId())
                    .token(token)
                    .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                    .build();
            tokenRepository.save(resetToken);

            String link = frontendUrl + "/reset-password?token=" + token;
            emailService.sendPasswordReset(user.getEmail(), link);
        });
    }

    public void resetPassword(ResetPasswordRequest req) {
        PasswordResetToken token = tokenRepository.findByToken(req.token())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This reset link is invalid or has already been used."));

        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This reset link has expired. Request a new one.");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        activityLogService.log(user.getBusinessId(), user.getId(), user.getFullName() + " reset their password", "USER", user.getId());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
