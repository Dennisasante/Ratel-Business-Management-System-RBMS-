package com.ratel.rbms.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratel.rbms.entity.enums.BillingStatus;
import com.ratel.rbms.repository.BusinessRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The actual security boundary for read-only enforcement — the frontend banner
 * (see the dashboard AppShell) is UX only. Runs right after JwtAuthenticationFilter
 * so TenantContext is already populated for business-scoped requests, and blocks
 * any mutating request from a business whose billing_status is READ_ONLY.
 *
 * Exempt regardless of billing status: /billing/** (a business must always be
 * able to reach the one place that lets it pay its way out of this), /auth/**
 * (logging in/out isn't a "mutation" in the sense this guards against), and
 * /platform/** (the super admin is never locked out of their own system).
 * Unauthenticated requests (e.g. the Paystack webhook) and platform-admin
 * requests never populate TenantContext's business id at all, so they pass
 * through this filter naturally without needing their own exemption rule.
 */
@Component
public class ReadOnlyEnforcementFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final List<String> EXEMPT_PREFIXES = List.of("/api/billing/", "/api/auth/", "/api/platform/");

    private final BusinessRepository businessRepository;
    private final ObjectMapper objectMapper;

    public ReadOnlyEnforcementFilter(BusinessRepository businessRepository, ObjectMapper objectMapper) {
        this.businessRepository = businessRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (!MUTATING_METHODS.contains(request.getMethod()) || isExempt(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID businessId = TenantContext.getBusinessIdOrNull();
        if (businessId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        BillingStatus status = businessRepository.findBillingStatusById(businessId).orElse(null);
        if (status == BillingStatus.READ_ONLY) {
            writePaymentRequired(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExempt(String path) {
        return EXEMPT_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void writePaymentRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
        response.setContentType("application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.PAYMENT_REQUIRED.value());
        body.put("error", "Your subscription has ended — renew to keep creating and editing.");

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
