package com.ratel.rbms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    // Distinguishes a business-user token (has businessId/role) from a
    // platform-admin token (has neither) — the auth filter branches on this
    // instead of guessing from which claims happen to be present.
    public static final String TYPE_BUSINESS_USER = "BUSINESS_USER";
    public static final String TYPE_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        // HS256 needs a key of at least 256 bits; padding keeps short dev secrets from blowing up at boot.
        String padded = secret.length() >= 32 ? secret : String.format("%-32s", secret).replace(' ', '0');
        this.signingKey = Keys.hmacShaKeyFor(padded.getBytes());
        this.expirationMs = expirationMs;
    }

    public String generateToken(UUID userId, UUID businessId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of(
                        "type", TYPE_BUSINESS_USER,
                        "businessId", businessId.toString(),
                        "role", role
                ))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String generatePlatformAdminToken(UUID adminId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(adminId.toString())
                .claims(Map.of("type", TYPE_PLATFORM_ADMIN))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractType(String token) {
        // Tokens issued before this field existed have no "type" claim — treat those as business tokens.
        String type = parseClaims(token).get("type", String.class);
        return type != null ? type : TYPE_BUSINESS_USER;
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public UUID extractBusinessId(String token) {
        return UUID.fromString(parseClaims(token).get("businessId", String.class));
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }
}
