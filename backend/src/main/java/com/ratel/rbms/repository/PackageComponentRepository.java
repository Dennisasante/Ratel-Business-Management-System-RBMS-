package com.ratel.rbms.repository;

import com.ratel.rbms.entity.PackageComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PackageComponentRepository extends JpaRepository<PackageComponent, UUID> {

    List<PackageComponent> findAllByBusinessIdAndOfferingId(UUID businessId, UUID offeringId);

    List<PackageComponent> findAllByBusinessIdAndOfferingIdOrderByDisplayOrderAsc(UUID businessId, UUID offeringId);

    Optional<PackageComponent> findByIdAndBusinessId(UUID id, UUID businessId);

    // Phase 5C — the package-content backfill's own idempotency lookup (Revision 4 §3/§6):
    // "does a canonical component already exist for this legacy ServicePackageItem, under this
    // Offering, for this business" — reused on rerun to reconcile rather than duplicate.
    Optional<PackageComponent> findByBusinessIdAndOfferingIdAndLegacyServicePackageItemId(
            UUID businessId, UUID offeringId, UUID legacyServicePackageItemId);
}
