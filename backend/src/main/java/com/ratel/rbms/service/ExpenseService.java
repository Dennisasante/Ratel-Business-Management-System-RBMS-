package com.ratel.rbms.service;

import com.ratel.rbms.dto.ExpenseRequest;
import com.ratel.rbms.dto.ExpenseResponse;
import com.ratel.rbms.entity.Expense;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.ExpenseRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            UserRepository userRepository,
            ActivityLogService activityLogService
    ) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
    }

    public List<ExpenseResponse> listAll() {
        return expenseRepository.findAllByBusinessIdOrderByExpenseDateDesc(TenantContext.getBusinessId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public ExpenseResponse get(UUID id) {
        return toResponse(getOwned(id));
    }

    public Expense getOwned(UUID id) {
        return expenseRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Expense not found."));
    }

    public ExpenseResponse create(ExpenseRequest req) {
        Expense expense = Expense.builder()
                .businessId(TenantContext.getBusinessId())
                .category(req.category())
                .description(req.description())
                .paymentMethod(req.paymentMethod())
                .amount(req.amount())
                .expenseDate(req.expenseDate() != null ? req.expenseDate() : LocalDate.now())
                .recordedBy(TenantContext.getUserId())
                .build();
        expense = expenseRepository.save(expense);
        activityLogService.log(
                "Logged expense: " + expense.getCategory().name() + " GH₵" + expense.getAmount(),
                "EXPENSE", expense.getId()
        );
        return toResponse(expense);
    }

    public ExpenseResponse update(UUID id, ExpenseRequest req) {
        Expense expense = getOwned(id);
        expense.setCategory(req.category());
        expense.setDescription(req.description());
        expense.setPaymentMethod(req.paymentMethod());
        expense.setAmount(req.amount());
        if (req.expenseDate() != null) expense.setExpenseDate(req.expenseDate());
        expense = expenseRepository.save(expense);
        return toResponse(expense);
    }

    public List<Expense> listBetween(LocalDate from, LocalDate to) {
        return expenseRepository.findAllByBusinessIdAndExpenseDateBetween(TenantContext.getBusinessId(), from, to);
    }

    private ExpenseResponse toResponse(Expense expense) {
        String recordedByName = expense.getRecordedBy() != null
                ? userRepository.findById(expense.getRecordedBy()).map(User::getFullName).orElse("Unknown")
                : "Unknown";
        return ExpenseResponse.from(expense, recordedByName);
    }
}
