package com.ratel.rbms.tenant;

import java.util.UUID;

/**
 * Holds the current request's tenant (business) and user id, resolved once from the
 * JWT in {@link JwtAuthenticationFilter} and read everywhere downstream (services,
 * controllers) instead of threading business_id through every method signature.
 *
 * Cleared at the end of every request by the same filter to avoid leaking state
 * across threads in the servlet container's thread pool.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_BUSINESS_ID = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_USER_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setBusinessId(UUID businessId) {
        CURRENT_BUSINESS_ID.set(businessId);
    }

    public static UUID getBusinessId() {
        UUID id = CURRENT_BUSINESS_ID.get();
        if (id == null) {
            throw new IllegalStateException("No business_id in tenant context — request wasn't authenticated correctly.");
        }
        return id;
    }

    public static void setUserId(UUID userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static UUID getUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void clear() {
        CURRENT_BUSINESS_ID.remove();
        CURRENT_USER_ID.remove();
    }
}
