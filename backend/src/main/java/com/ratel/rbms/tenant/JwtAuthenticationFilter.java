package com.ratel.rbms.tenant;

import com.ratel.rbms.security.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Every authenticated request carries a Bearer JWT. This filter verifies it once,
 * then branches on the token's "type" claim:
 *
 *  - BUSINESS_USER tokens stamp business_id + user_id into TenantContext, so
 *    every repository query and service method downstream can scope itself to
 *    the current tenant without the caller having to remember to pass it around.
 *  - PLATFORM_ADMIN tokens (the Super Admin) carry no business_id at all —
 *    they get a ROLE_SUPER_ADMIN authority instead, and TenantContext is left
 *    unset, since a platform admin isn't scoped to any single business.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            String type = jwtService.extractType(token);

            if (JwtService.TYPE_PLATFORM_ADMIN.equals(type)) {
                UUID adminId = jwtService.extractUserId(token);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
                var authentication = new UsernamePasswordAuthenticationToken(adminId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                UUID userId = jwtService.extractUserId(token);
                UUID businessId = jwtService.extractBusinessId(token);
                String role = jwtService.extractRole(token);

                TenantContext.setUserId(userId);
                TenantContext.setBusinessId(businessId);
                TenantContext.setRole(role);

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
        } finally {
            TenantContext.clear();
        }
    }
}
