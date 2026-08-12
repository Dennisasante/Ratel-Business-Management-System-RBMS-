package com.ratel.rbms.repository;

import com.ratel.rbms.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
            + "(t.createdAt >= :from OR CAST(:from AS timestamp) IS NULL) AND "
            + "(t.createdAt < :to OR CAST(:to AS timestamp) IS NULL) "
            + "ORDER BY t.createdAt DESC")
    List<PaymentTransaction> search(
            @Param("businessId") UUID businessId,
            @Param("direction") PaymentTransaction.Direction direction,
            @Param("gateway") String gateway,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
