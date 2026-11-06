package com.ratel.rbms.repository;

import com.ratel.rbms.entity.LoyaltyCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoyaltyCustomerRepository extends JpaRepository<LoyaltyCustomer, UUID> {

    /** A fresh database read of a customer's counters — never a cached entity. */
    interface Standing {
        UUID getCustomerId();

        String getToken();

        int getVisitsTotal();

        int getVisitsRedeemed();
    }

    @Query("SELECT c.customerId AS customerId, c.token AS token, c.visitsTotal AS visitsTotal, c.visitsRedeemed AS visitsRedeemed "
            + "FROM LoyaltyCustomer c WHERE c.businessId = :businessId AND c.customerId = :customerId")
    Optional<Standing> standing(@Param("businessId") UUID businessId, @Param("customerId") UUID customerId);

    @Query("SELECT c.customerId AS customerId, c.token AS token, c.visitsTotal AS visitsTotal, c.visitsRedeemed AS visitsRedeemed "
            + "FROM LoyaltyCustomer c WHERE c.businessId = :businessId")
    List<Standing> standings(@Param("businessId") UUID businessId);

    Optional<LoyaltyCustomer> findByToken(String token);

    /** Has a visit already been counted for this customer on this day? (Only one is allowed per day.) */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM loyalty_visits WHERE business_id = :businessId "
            + "AND customer_id = :customerId AND visit_date = :day)", nativeQuery = true)
    boolean visitedOn(@Param("businessId") UUID businessId, @Param("customerId") UUID customerId, @Param("day") LocalDate day);

    /**
     * Customers of a business whose phone ends in these nine digits, whatever way the number was typed
     * (0244..., +233 244..., 233244...). A handful at most — duplicates are possible.
     */
    @Query(value = "SELECT c.id FROM customers c WHERE c.business_id = :businessId AND c.phone IS NOT NULL "
            + "AND right(regexp_replace(c.phone, '[^0-9]', '', 'g'), 9) = :suffix ORDER BY c.created_at LIMIT 5", nativeQuery = true)
    List<UUID> findCustomerIdsByPhoneSuffix(@Param("businessId") UUID businessId, @Param("suffix") String suffix);

    @Modifying
    @Query(value = "INSERT INTO loyalty_customers (business_id, customer_id, token) VALUES (:businessId, :customerId, :token) "
            + "ON CONFLICT (business_id, customer_id) DO NOTHING", nativeQuery = true)
    void createIfMissing(@Param("businessId") UUID businessId, @Param("customerId") UUID customerId, @Param("token") String token);

    // The whole visit rule in one statement: record today's visit if there isn't one yet, and
    // only then bump the counter. A second sale the same day (or two racing at once) inserts
    // nothing and returns no row, so a day can never count twice. Returns the new total, or
    // null when today was already counted.
    @Query(value = "WITH ins AS (INSERT INTO loyalty_visits (business_id, customer_id, visit_date, source_type, source_id) "
            + "VALUES (:businessId, :customerId, :visitDate, :sourceType, :sourceId) "
            + "ON CONFLICT (business_id, customer_id, visit_date) DO NOTHING RETURNING 1) "
            + "UPDATE loyalty_customers SET visits_total = visits_total + 1 "
            + "WHERE business_id = :businessId AND customer_id = :customerId AND EXISTS (SELECT 1 FROM ins) "
            + "RETURNING visits_total", nativeQuery = true)
    Integer recordVisit(@Param("businessId") UUID businessId, @Param("customerId") UUID customerId,
                        @Param("visitDate") LocalDate visitDate, @Param("sourceType") String sourceType,
                        @Param("sourceId") UUID sourceId);

    // Consumes a reward's worth of visits, only if the customer really has them. Returns the new
    // redeemed total, or null (nothing changed) when they weren't due one.
    @Query(value = "UPDATE loyalty_customers SET visits_redeemed = visits_redeemed + :visits "
            + "WHERE business_id = :businessId AND customer_id = :customerId "
            + "AND visits_total - visits_redeemed >= :visits RETURNING visits_redeemed", nativeQuery = true)
    Integer redeem(@Param("businessId") UUID businessId, @Param("customerId") UUID customerId, @Param("visits") int visits);
}
