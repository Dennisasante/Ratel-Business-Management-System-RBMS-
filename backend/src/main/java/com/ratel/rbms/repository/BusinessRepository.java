package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.enums.BillingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
    List<Business> findByNameContainingIgnoreCase(String name);

    boolean existsBySlug(String slug);

    Optional<Business> findBySlug(String slug);

    // Locks the row for the rest of the transaction — used by BillingService.verifyPayment
    // so a concurrent webhook + client-triggered verify for the same business can't both
    // read a stale current_period_ends_at and independently extend it.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Business b WHERE b.id = :id")
    Optional<Business> findByIdForUpdate(@Param("id") UUID id);

    // Lean projection for ReadOnlyEnforcementFilter, which runs on every mutating
    // request and only needs this one column, not the full entity.
    @Query("SELECT b.billingStatus FROM Business b WHERE b.id = :id")
    Optional<BillingStatus> findBillingStatusById(@Param("id") UUID id);

    long countBySubscriptionPlanId(UUID planId);

    // Super admin platform stats: totalBusinesses uses the inherited count().
    long countByActive(boolean active);

    @Query("SELECT b.billingStatus AS billingStatus, COUNT(b) AS total FROM Business b GROUP BY b.billingStatus")
    List<BillingStatusCount> countGroupedByBillingStatus();

    @Query("SELECT b.subscriptionPlanId AS planId, COUNT(b) AS total FROM Business b "
            + "WHERE b.subscriptionPlanId IS NOT NULL GROUP BY b.subscriptionPlanId")
    List<PlanBusinessCount> countGroupedBySubscriptionPlan();

    // Same shape, but only businesses currently paying (ACTIVE) — used to
    // compute MRR per plan, since a TRIALING or READ_ONLY business still has
    // a subscriptionPlanId set but isn't actually contributing revenue.
    @Query("SELECT b.subscriptionPlanId AS planId, COUNT(b) AS total FROM Business b "
            + "WHERE b.subscriptionPlanId IS NOT NULL AND b.billingStatus = com.ratel.rbms.entity.enums.BillingStatus.ACTIVE "
            + "GROUP BY b.subscriptionPlanId")
    List<PlanBusinessCount> countActiveGroupedBySubscriptionPlan();

    // Signups-by-day chart — only needs businesses created within the window,
    // not a full-table load.
    List<Business> findAllByCreatedAtAfter(Instant cutoff);

    interface BillingStatusCount {
        BillingStatus getBillingStatus();
        long getTotal();
    }

    interface PlanBusinessCount {
        UUID getPlanId();
        long getTotal();
    }

    // Scheduled expiry job: still-trialing businesses whose trial has lapsed.
    List<Business> findAllByBillingStatusAndTrialEndsAtBefore(BillingStatus status, Instant cutoff);

    // Scheduled expiry job: still-active businesses whose paid period has lapsed.
    List<Business> findAllByBillingStatusAndCurrentPeriodEndsAtBefore(BillingStatus status, Instant cutoff);

    // Reminder email: businesses whose deadline falls within the warning window and
    // haven't been reminded yet (expiryReminderSentAt is null).
    List<Business> findAllByBillingStatusAndTrialEndsAtBetweenAndExpiryReminderSentAtIsNull(
            BillingStatus status, Instant from, Instant to);

    List<Business> findAllByBillingStatusAndCurrentPeriodEndsAtBetweenAndExpiryReminderSentAtIsNull(
            BillingStatus status, Instant from, Instant to);
}
