package com.ratel.rbms.config;

import com.ratel.rbms.entity.PlatformAdmin;
import com.ratel.rbms.repository.PlatformAdminRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the platform Super Admin account on startup, if SUPER_ADMIN_EMAIL
 * and SUPER_ADMIN_PASSWORD are set and no admin exists yet.
 *
 * This is deliberately the only way to create a Super Admin — there is no
 * public registration route for it, and never should be. After the first
 * successful run, it's safe (and recommended) to unset these env vars again;
 * the seeder becomes a no-op once an admin exists, so leaving them set is
 * harmless but unnecessary.
 */
@Component
public class SuperAdminSeeder implements ApplicationRunner {

    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String fullName;

    public SuperAdminSeeder(
            PlatformAdminRepository platformAdminRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.super-admin.email}") String email,
            @Value("${app.super-admin.password}") String password,
            @Value("${app.super-admin.full-name}") String fullName
    ) {
        this.platformAdminRepository = platformAdminRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (platformAdminRepository.count() > 0) {
            return; // a Super Admin already exists — never overwrite or create a second one silently
        }

        if (email.isBlank() || password.isBlank()) {
            System.out.println("[RBMS] No Super Admin exists yet, and SUPER_ADMIN_EMAIL/SUPER_ADMIN_PASSWORD "
                    + "aren't set — set them and restart to create one. See backend README.");
            return;
        }

        PlatformAdmin admin = PlatformAdmin.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(fullName)
                .build();
        platformAdminRepository.save(admin);

        System.out.println("[RBMS] Super Admin account created for " + email
                + " — you can unset SUPER_ADMIN_EMAIL/SUPER_ADMIN_PASSWORD now.");
    }
}
