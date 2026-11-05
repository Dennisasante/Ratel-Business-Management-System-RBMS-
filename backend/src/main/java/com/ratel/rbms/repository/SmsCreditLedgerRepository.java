package com.ratel.rbms.repository;

import com.ratel.rbms.entity.SmsCreditLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SmsCreditLedgerRepository extends JpaRepository<SmsCreditLedger, UUID> {

    List<SmsCreditLedger> findTop50ByBusinessIdOrderByCreatedAtDesc(UUID businessId);
}
