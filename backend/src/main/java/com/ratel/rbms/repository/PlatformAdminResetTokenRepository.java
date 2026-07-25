package com.ratel.rbms.repository;

import com.ratel.rbms.entity.PlatformAdminResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformAdminResetTokenRepository extends JpaRepository<PlatformAdminResetToken, UUID> {
    Optional<PlatformAdminResetToken> findByToken(String token);
}
