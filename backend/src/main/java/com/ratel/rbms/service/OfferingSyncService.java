package com.ratel.rbms.service;

import com.ratel.rbms.entity.Offering;
import com.ratel.rbms.entity.OfferingBookingConfig;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServicePackage;
import com.ratel.rbms.repository.OfferingBookingConfigRepository;
import com.ratel.rbms.repository.OfferingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Talia Unified Platform, Phase 5A — the ONE authorized writer of the canonical Offering/
 * OfferingBookingConfig side effect for a legacy write. Called only from inside an already-open
 * {@code @Transactional} legacy-mutation method ({@code ServiceCatalogService}/
 * {@code ServicePackageService}) — never independently, never from a background job. This is the
 * "one authorized operation, fanning out to two representations" design: there is exactly one
 * user-facing editor (the legacy entity's own existing service), this class only mirrors that
 * single write onto the linked canonical rows, inside the SAME transaction, so a failure here
 * rolls back the legacy write too.
 *
 * <p>Upsert shape deliberately used for both methods below (find-the-linked-row-or-create-it,
 * then apply current legacy values) so create/update/setActive all funnel through one code path
 * each per legacy type — avoids duplicating the "what does this legacy item's canonical shape
 * look like" logic three times.
 */
@Service
public class OfferingSyncService {

    private final OfferingRepository offeringRepository;
    private final OfferingBookingConfigRepository offeringBookingConfigRepository;

    public OfferingSyncService(OfferingRepository offeringRepository,
                                OfferingBookingConfigRepository offeringBookingConfigRepository) {
        this.offeringRepository = offeringRepository;
        this.offeringBookingConfigRepository = offeringBookingConfigRepository;
    }

    @Transactional
    public void syncServiceCatalogItem(ServiceCatalogItem item) {
        Offering offering = offeringRepository
                .findByBusinessIdAndLegacyServiceCatalogId(item.getBusinessId(), item.getId())
                .orElseGet(() -> offeringRepository.save(Offering.builder()
                        .businessId(item.getBusinessId())
                        .type("SERVICE")
                        .legacyServiceCatalogId(item.getId())
                        .name(item.getName())
                        .basePrice(item.getPrice())
                        .active(item.isActive())
                        .build()));
        offering.setName(item.getName());
        offering.setBasePrice(item.getPrice());
        offering.setActive(item.isActive());
        offeringRepository.save(offering);

        OfferingBookingConfig config = offeringBookingConfigRepository
                .findByOfferingIdAndBusinessId(offering.getId(), item.getBusinessId())
                .orElseGet(() -> OfferingBookingConfig.builder()
                        .offeringId(offering.getId()).businessId(item.getBusinessId()).build());
        config.setBookableOnline(item.isBookableOnline());
        config.setDurationMinutes(item.getDurationMinutes());
        config.setMaxConcurrentBookings(item.getMaxConcurrentBookings());
        config.setRequiresLocation(item.isRequiresLocation());
        config.setPaymentPolicyOverride(item.getPaymentPolicyOverride());
        offeringBookingConfigRepository.save(config);
    }

    @Transactional
    public void syncServicePackage(ServicePackage pkg) {
        Offering offering = offeringRepository
                .findByBusinessIdAndLegacyServicePackageId(pkg.getBusinessId(), pkg.getId())
                .orElseGet(() -> offeringRepository.save(Offering.builder()
                        .businessId(pkg.getBusinessId())
                        .type("PACKAGE")
                        .legacyServicePackageId(pkg.getId())
                        .name(pkg.getName())
                        .basePrice(pkg.getPrice())
                        .active(pkg.isActive())
                        .build()));
        offering.setName(pkg.getName());
        offering.setBasePrice(pkg.getPrice());
        offering.setActive(pkg.isActive());
        offeringRepository.save(offering);

        OfferingBookingConfig config = offeringBookingConfigRepository
                .findByOfferingIdAndBusinessId(offering.getId(), pkg.getBusinessId())
                .orElseGet(() -> OfferingBookingConfig.builder()
                        .offeringId(offering.getId()).businessId(pkg.getBusinessId())
                        // ServicePackage has no requiresLocation column at all — documented
                        // default, preserves BookingService's own current hardcoded behaviour
                        // for packages exactly. Only set at creation, deliberately never
                        // overwritten on a routine legacy update below (see class comment on
                        // syncServicePackage's own field list) — there is no legacy source value
                        // to sync it from, and Phase 5A introduces no way to edit it either, so a
                        // routine package edit must never silently reset it back to false.
                        .requiresLocation(false)
                        .build());
        config.setBookableOnline(pkg.isBookableOnline());
        config.setDurationMinutes(pkg.getDurationMinutes());
        config.setMaxConcurrentBookings(pkg.getMaxConcurrentBookings());
        config.setPaymentPolicyOverride(pkg.getPaymentPolicyOverride());
        offeringBookingConfigRepository.save(config);
    }
}
