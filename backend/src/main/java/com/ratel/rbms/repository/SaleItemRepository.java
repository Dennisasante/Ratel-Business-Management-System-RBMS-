package com.ratel.rbms.repository;

import com.ratel.rbms.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {

    List<SaleItem> findAllBySaleId(UUID saleId);
}
