package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Offering;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2 read/write model. Phase 5A adds the tenant-scoped legacy-mapping lookups —
 * {@link com.ratel.rbms.service.OfferingResolutionService} is the only approved caller of the
 * two {@code findByBusinessIdAndLegacy*} methods; no other service should look these up directly.
 */
public interface OfferingRepository extends JpaRepository<Offering, UUID> {

    Optional<Offering> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<Offering> findByBusinessIdAndLegacyServiceCatalogId(UUID businessId, UUID legacyServiceCatalogId);

    Optional<Offering> findByBusinessIdAndLegacyServicePackageId(UUID businessId, UUID legacyServicePackageId);

    // Phase 5C — the capacity-race correctness fix (Revision 4 §5/frozen design §10): locks the
    // Offering row for the rest of the caller's transaction, BEFORE any capacity-dependent
    // count/insert. Same @Lock(PESSIMISTIC_WRITE) + explicit @Query pattern as
    // BusinessRepository.findByIdForUpdate, this codebase's own existing precedent.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Offering o WHERE o.id = :id AND o.businessId = :businessId")
    Optional<Offering> findByIdAndBusinessIdForUpdate(@Param("id") UUID id, @Param("businessId") UUID businessId);
}
