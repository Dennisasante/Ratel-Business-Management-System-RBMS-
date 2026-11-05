package com.ratel.rbms.repository;

import com.ratel.rbms.entity.SmsCreditPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SmsCreditPurchaseRepository extends JpaRepository<SmsCreditPurchase, UUID> {

    Optional<SmsCreditPurchase> findByPaystackReference(String paystackReference);

    List<SmsCreditPurchase> findTop20ByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    List<SmsCreditPurchase> findAllByStatusAndCreatedAtBefore(String status, Instant before);

    // The idempotency guard for crediting: only the one caller whose UPDATE
    // actually flips the row to SUCCESS (webhook, client verify, or the
    // reconciliation sweep, in any order) ever gets 1 back, so a purchase's
    // credits can never be applied twice. A FAILED row can still flip — it was
    // only ever marked failed on a non-final Paystack status, and if Paystack
    // later confirms the money, the customer must get their credits.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SmsCreditPurchase p SET p.status = 'SUCCESS', p.paidAt = :now "
            + "WHERE p.paystackReference = :reference AND p.status <> 'SUCCESS'")
    int markSuccessIfNotAlready(@Param("reference") String reference, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SmsCreditPurchase p SET p.status = 'FAILED' "
            + "WHERE p.paystackReference = :reference AND p.status = 'PENDING'")
    int markFailedIfPending(@Param("reference") String reference);
}
