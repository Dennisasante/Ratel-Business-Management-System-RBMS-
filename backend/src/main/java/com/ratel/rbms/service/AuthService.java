package com.ratel.rbms.service;

import com.ratel.rbms.dto.AuthResponse;
import com.ratel.rbms.dto.ChangePasswordRequest;
import com.ratel.rbms.dto.GoogleLoginRequest;
import com.ratel.rbms.dto.GoogleRegisterRequest;
import com.ratel.rbms.dto.LoginRequest;
import com.ratel.rbms.dto.RegisterBusinessRequest;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.AuthProvider;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.PlatformBillingSettingsRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.security.GoogleTokenVerifier;
import com.ratel.rbms.security.JwtService;
import com.ratel.rbms.security.RateLimiterService;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ActivityLogService activityLogService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final RateLimiterService rateLimiterService;
    private final PlatformBillingSettingsRepository platformBillingSettingsRepository;

    public AuthService(
            BusinessRepository businessRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ActivityLogService activityLogService,
            GoogleTokenVerifier googleTokenVerifier,
            RateLimiterService rateLimiterService,
            PlatformBillingSettingsRepository platformBillingSettingsRepository
    ) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.activityLogService = activityLogService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.rateLimiterService = rateLimiterService;
        this.platformBillingSettingsRepository = platformBillingSettingsRepository;
    }

    @Transactional
    public AuthResponse registerBusiness(RegisterBusinessRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        Business business = createBusiness(req.businessName(), req.industry(), req.location(), req.email(), req.contactPhone());

        User owner = User.builder()
                .businessId(business.getId())
                .fullName(req.ownerFullName())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .authProvider(AuthProvider.LOCAL)
                .role(Role.OWNER)
                .build();
        owner = userRepository.save(owner);

        activityLogService.log(business.getId(), owner.getId(), "Business registered — \"" + business.getName() + "\" created by " + owner.getFullName(), "BUSINESS", business.getId());

        return issueResponse(owner, business);
    }

    public AuthResponse login(LoginRequest req) {
        String rateLimitKey = "login:" + req.email().toLowerCase();
        rateLimiterService.checkAllowed(rateLimitKey, 5, Duration.ofMinutes(15));

        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> {
                    rateLimiterService.recordAttempt(rateLimitKey);
                    return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
                });

        // Google-only accounts have no password_hash — reject local login for them
        // with the same generic message rather than leaking which accounts exist.
        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            rateLimiterService.recordAttempt(rateLimitKey);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        rateLimiterService.reset(rateLimitKey);
        return completeLogin(user);
    }

    @Transactional
    public AuthResponse registerBusinessWithGoogle(GoogleRegisterRequest req) {
        GoogleTokenVerifier.GoogleUser googleUser = googleTokenVerifier.verify(req.idToken());

        if (userRepository.findByEmail(googleUser.email()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists — try logging in instead.");
        }

        Business business = createBusiness(req.businessName(), req.industry(), req.location(), googleUser.email(), req.contactPhone());

        User owner = User.builder()
                .businessId(business.getId())
                .fullName(googleUser.name())
                .email(googleUser.email())
                .passwordHash(null)
                .authProvider(AuthProvider.GOOGLE)
                .role(Role.OWNER)
                .build();
        owner = userRepository.save(owner);

        activityLogService.log(business.getId(), owner.getId(), "Business registered via Google — \"" + business.getName() + "\" created by " + owner.getFullName(), "BUSINESS", business.getId());

        return issueResponse(owner, business);
    }

    public AuthResponse loginWithGoogle(GoogleLoginRequest req) {
        GoogleTokenVerifier.GoogleUser googleUser = googleTokenVerifier.verify(req.idToken());

        User user = userRepository.findByEmail(googleUser.email())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No account found for this Google email. Register your business first."));

        return completeLogin(user);
    }

    private Business createBusiness(String name, com.ratel.rbms.entity.enums.Industry industry, String location, String contactEmail, String contactPhone) {
        // billingStatus defaults to TRIALING and subscriptionPlanId stays null
        // until this business's first successful payment (see BillingService).
        int trialDays = platformBillingSettingsRepository.findFirstByOrderByUpdatedAtDesc().getTrialDays();

        Business business = Business.builder()
                .name(name)
                .industry(industry)
                .location(location)
                .contactEmail(contactEmail)
                .contactPhone(contactPhone)
                .trialEndsAt(Instant.now().plus(trialDays, ChronoUnit.DAYS))
                .build();
        return businessRepository.save(business);
    }

    private AuthResponse completeLogin(User user) {
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account has been deactivated.");
        }

        Business business = businessRepository.findById(user.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found for this account."));

        if (!business.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This business account has been suspended. Contact support.");
        }

        activityLogService.log(business.getId(), user.getId(), user.getFullName() + " logged in", "USER", user.getId());

        return issueResponse(user, business);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        User user = userRepository.findById(TenantContext.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        activityLogService.log("Changed their password", "USER", user.getId());
    }

    private AuthResponse issueResponse(User user, Business business) {
        String token = jwtService.generateToken(user.getId(), business.getId(), user.getRole().name());
        return new AuthResponse(
                token,
                user.getId(),
                business.getId(),
                business.getName(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.isMustChangePassword()
        );
    }
}
