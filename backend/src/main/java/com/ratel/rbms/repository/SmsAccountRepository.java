package com.ratel.rbms.repository;

import com.ratel.rbms.entity.BusinessSmsAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SmsAccountRepository extends JpaRepository<BusinessSmsAccount, UUID> {

    // Idempotent and race-safe: two first-ever requests for the same business
    // can't both insert.
    @Modifying
    @Query(value = "INSERT INTO business_sms_accounts (business_id) VALUES (:businessId) ON CONFLICT (business_id) DO NOTHING",
            nativeQuery = true)
    void createIfMissing(@Param("businessId") UUID businessId);

    // The ONLY way credits leave an account. One conditional statement, so two
    // concurrent sends can never both spend the last credit. Returns the new
    // balance, or null (no row matched) when the balance was too low — in which
    // case nothing changed.
    @Query(value = "UPDATE business_sms_accounts SET credit_balance = credit_balance - :credits, updated_at = now() "
            + "WHERE business_id = :businessId AND credit_balance >= :credits RETURNING credit_balance",
            nativeQuery = true)
    Integer debit(@Param("businessId") UUID businessId, @Param("credits") int credits);

    // The ONLY way credits enter an account. Returns the new balance.
    @Query(value = "UPDATE business_sms_accounts SET credit_balance = credit_balance + :credits, updated_at = now() "
            + "WHERE business_id = :businessId RETURNING credit_balance",
            nativeQuery = true)
    Integer credit(@Param("businessId") UUID businessId, @Param("credits") int credits);

    @Query("SELECT COALESCE(SUM(a.creditBalance), 0) FROM BusinessSmsAccount a")
    long totalOutstandingCredits();

    // A scalar read always goes to the database — reading the entity's own
    // creditBalance can be stale within one web request (open-in-view keeps the
    // persistence context alive across the native debit/credit queries above).
    @Query("SELECT a.creditBalance FROM BusinessSmsAccount a WHERE a.businessId = :businessId")
    Integer balanceOf(@Param("businessId") UUID businessId);
}
