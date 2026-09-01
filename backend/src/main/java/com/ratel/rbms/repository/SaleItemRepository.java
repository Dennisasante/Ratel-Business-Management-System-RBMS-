package com.ratel.rbms.repository;

import com.ratel.rbms.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {

    List<SaleItem> findAllBySaleId(UUID saleId);

    Optional<SaleItem> findByIdAndSaleId(UUID id, UUID saleId);

    // Backs dashboard profitability aggregation (COGS/gross profit/top
    // products) — the caller already resolved which sales fall in the
    // date range (see DashboardService), this just pulls their line items
    // in one query instead of one round trip per sale.
    List<SaleItem> findAllBySaleIdIn(List<UUID> saleIds);
}
