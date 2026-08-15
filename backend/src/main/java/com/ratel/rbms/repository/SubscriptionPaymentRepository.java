package com.ratel.rbms.repository;

import com.ratel.rbms.entity.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, UUID> {

    List<SubscriptionPayment> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    // Actual money businesses have paid Tallia for their own subscriptions,
    // platform-wide, all-time — distinct from PlatformStatsService's existing
    // "platform revenue" figure, which is GMV each business collected from
    // ITS OWN customers (via PaymentTransaction), not what they paid Tallia.
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SubscriptionPayment p WHERE p.status = 'SUCCESS'")
    BigDecimal sumAmountAllTime();

    // The idempotency lookup: a webhook and a client-triggered verify for the
    // same payment resolve to the same row instead of double-extending the period.
    Optional<SubscriptionPayment> findByPaystackReference(String paystackReference);

    // Atomic guard: flips PENDING/FAILED -> SUCCESS only if it isn't already SUCCESS,
    // and reports how many rows it actually changed (0 or 1). BillingService only
    // extends a business's period when this returns 1 — if a racing call already won
    // (this returns 0), the business is left untouched to avoid double-extension.
    @Modifying
    @Query("UPDATE SubscriptionPayment p SET p.status = 'SUCCESS', p.paidAt = :paidAt, " +
            "p.periodStart = :periodStart, p.periodEnd = :periodEnd " +
            "WHERE p.paystackReference = :reference AND p.status <> 'SUCCESS'")
    int markSuccessIfNotAlready(
            @Param("reference") String reference,
            @Param("paidAt") Instant paidAt,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd
    );

    // A direct targeted update rather than fetch-mutate-save — the `payment`
    // entity BillingService.verifyPayment() holds in memory is stale by this
    // point (markSuccessIfNotAlready() above is a bulk update that bypasses
    // the persistence context), so saving that object here would silently
    // overwrite the bulk update's status/paidAt/period columns with their
    // pre-update values.
    @Modifying
    @Query("UPDATE SubscriptionPayment p SET p.authorizationCode = :authorizationCode WHERE p.paystackReference = :reference")
    void updateAuthorizationCode(@Param("reference") String reference, @Param("authorizationCode") String authorizationCode);
}
