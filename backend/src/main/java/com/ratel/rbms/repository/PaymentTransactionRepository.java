package com.ratel.rbms.repository;

import com.ratel.rbms.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByGatewayReferenceAndBusinessId(String gatewayReference, UUID businessId);

    // Used when Super Admin deletes the source record (a test service order/sale)
    // so its ledger entries don't become orphaned rows pointing at nothing.
    void deleteBySourceTypeAndSourceId(PaymentTransaction.SourceType sourceType, UUID sourceId);

    // Backs both the owner-facing Payments page and the Super Admin per-business
    // view — every param is optional, same convention as ServiceOrderRepository.search.
    // Each JPQL occurrence of a named parameter compiles to its OWN separate `?`
    // placeholder in the generated SQL — Postgres infers each `?`'s type independently
    // at parse time, so a bare "?  IS NULL" with no column-comparison context of its
    // own is unresolvable ("could not determine data type of parameter") even when
    // the SAME named parameter is compared against a typed column elsewhere in the
    // query. Every standalone "IS NULL" occurrence below is explicitly cast for
    // exactly this reason — same root cause as this codebase's existing
    // CAST(:search AS string) gotcha (see ProductRepository.search), just hitting
    // every occurrence instead of only the first.
    @Query("SELECT t FROM PaymentTransaction t WHERE t.businessId = :businessId AND "
            + "(t.direction = :direction OR CAST(:direction AS string) IS NULL) AND "
            + "(t.gateway = :gateway OR CAST(:gateway AS string) IS NULL) AND "
            + "(t.createdBy = :createdBy OR CAST(:createdBy AS uuid) IS NULL) AND "
            + "(t.createdAt >= :from OR CAST(:from AS timestamp) IS NULL) AND "
            + "(t.createdAt < :to OR CAST(:to AS timestamp) IS NULL) "
            + "ORDER BY t.createdAt DESC")
    List<PaymentTransaction> search(
            @Param("businessId") UUID businessId,
            @Param("direction") PaymentTransaction.Direction direction,
            @Param("gateway") String gateway,
            @Param("createdBy") UUID createdBy,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    // Money actually collected in a date range, for one business — regardless
    // of which entity type it came from (Sale, ServiceOrder, Booking,
    // PurchaseOrder, CustomWigRequest, or anything added later). Backs
    // ReportService.summary()'s revenue figure so a newly-added money-
    // collecting entity type is automatically included, the same way the
    // Payments page (built on search() above) already is — no one-off fix
    // needed the next time a new source type is added.
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PaymentTransaction t WHERE t.businessId = :businessId "
            + "AND t.direction = :direction AND t.status = :status AND t.createdAt >= :from AND t.createdAt < :to")
    BigDecimal sumAmount(
            @Param("businessId") UUID businessId,
            @Param("direction") PaymentTransaction.Direction direction,
            @Param("status") String status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    // Money paid BACK out on a refund in a date range — a Sale/ServiceOrder/
    // Booking/CustomWigRequest refund is recorded as an OUTGOING transaction
    // against that same SourceType (see SaleService.doRefund() and its
    // ServiceOrder/CustomWigRequest equivalents), which is what distinguishes
    // it from a PURCHASE_ORDER outgoing payment (money paid to a supplier —
    // never a "refund" and never something Revenue should net against).
    // Backs ReportService.revenue()'s "money actually collected, net of
    // refunds" figure.
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PaymentTransaction t WHERE t.businessId = :businessId "
            + "AND t.direction = :direction AND t.status = :status AND t.sourceType <> :excludedSourceType "
            + "AND t.createdAt >= :from AND t.createdAt < :to")
    BigDecimal sumRefunds(
            @Param("businessId") UUID businessId,
            @Param("direction") PaymentTransaction.Direction direction,
            @Param("status") String status,
            @Param("excludedSourceType") PaymentTransaction.SourceType excludedSourceType,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    // Same as sumAmount() above but platform-wide, all-time, no business
    // filter — backs PlatformStatsService's "platform revenue" figure.
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PaymentTransaction t WHERE t.direction = :direction AND t.status = :status")
    BigDecimal sumAmountAllTime(@Param("direction") PaymentTransaction.Direction direction, @Param("status") String status);

    // Same as sumAmount() but platform-wide with a date range — backs the
    // Super Admin weekly digest's "platform revenue this week" figure.
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PaymentTransaction t WHERE t.direction = :direction "
            + "AND t.status = :status AND t.createdAt >= :from AND t.createdAt < :to")
    BigDecimal sumAmountByDateRange(
            @Param("direction") PaymentTransaction.Direction direction,
            @Param("status") String status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    // Backs the dashboard revenue chart's daily/weekly/monthly buckets — the
    // full list (not just a sum) so DashboardService can bucket every
    // transaction in the range in one pass instead of one sumAmount() query
    // per bucket.
    List<PaymentTransaction> findAllByBusinessIdAndDirectionAndStatusAndCreatedAtBetween(
            UUID businessId, PaymentTransaction.Direction direction, String status, Instant from, Instant to
    );

    // "How was this most recently paid?" for a single source record — backs
    // CustomWigRequestDetailResponse.paymentMethod. Deliberately not a column
    // on CustomWigRequest itself (that would just be a copy of what the
    // ledger already knows, and could drift from it).
    Optional<PaymentTransaction> findFirstBySourceTypeAndSourceIdAndDirectionAndStatusOrderByCreatedAtDesc(
            PaymentTransaction.SourceType sourceType,
            UUID sourceId,
            PaymentTransaction.Direction direction,
            String status
    );
}
