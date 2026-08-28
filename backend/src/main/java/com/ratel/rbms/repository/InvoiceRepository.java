package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findAllByBusinessIdOrderByIssueDateDesc(UUID businessId);

    // Backs the Invoices page's date filter (defaults to Today).
    List<Invoice> findAllByBusinessIdAndIssueDateBetween(UUID businessId, LocalDate from, LocalDate to);

    Optional<Invoice> findByIdAndBusinessId(UUID id, UUID businessId);

    // Backs per-business sequential numbering — always called after
    // BusinessRepository.findByIdForUpdate(businessId) has already taken the
    // row lock, so a concurrent invoice creation for the same business can't
    // both read the same max and collide on the unique (business_id, invoice_number).
    @Query("SELECT COALESCE(MAX(i.invoiceNumber), 0) FROM Invoice i WHERE i.businessId = :businessId")
    long findMaxInvoiceNumber(@Param("businessId") UUID businessId);
}
