package com.ratel.rbms.repository;

import com.ratel.rbms.entity.ServiceOrderLineSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceOrderLineSnapshotRepository extends JpaRepository<ServiceOrderLineSnapshot, UUID> {

    // Historical display (Revision 4 §2/§13) — read the frozen snapshot, never re-resolve against
    // current Offering/Option/PackageComponent state.
    List<ServiceOrderLineSnapshot> findAllByBusinessIdAndServiceOrderId(UUID businessId, UUID serviceOrderId);
}
