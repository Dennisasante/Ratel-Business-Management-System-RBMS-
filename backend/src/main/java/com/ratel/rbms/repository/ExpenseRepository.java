package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findAllByBusinessIdOrderByExpenseDateDesc(UUID businessId);

    Optional<Expense> findByIdAndBusinessId(UUID id, UUID businessId);

    // Used by the reports endpoint to total expenses within a date range.
    List<Expense> findAllByBusinessIdAndExpenseDateBetween(UUID businessId, LocalDate from, LocalDate to);
}
