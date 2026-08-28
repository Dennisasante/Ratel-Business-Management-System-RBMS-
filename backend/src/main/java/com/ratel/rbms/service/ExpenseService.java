package com.ratel.rbms.service;

import com.ratel.rbms.dto.ExpenseEditRequest;
import com.ratel.rbms.dto.ExpenseRequest;
import com.ratel.rbms.dto.ExpenseResponse;
import com.ratel.rbms.entity.Expense;
import com.ratel.rbms.entity.PendingApproval;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.exception.ApprovalRequiredException;
import com.ratel.rbms.repository.ExpenseRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final ApprovalGateService approvalGateService;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            UserRepository userRepository,
            ActivityLogService activityLogService,
            ApprovalGateService approvalGateService
    ) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
        this.approvalGateService = approvalGateService;
    }

    public List<ExpenseResponse> listAll() {
        return list(null, null);
    }

    // Backs the Expenses page's date filter (defaults to Today, see
    // DateRangeFilter) — both-or-neither, null on both means "all time."
    public List<ExpenseResponse> list(LocalDate from, LocalDate to) {
        UUID businessId = TenantContext.getBusinessId();
        List<Expense> expenses = (from == null && to == null)
                ? expenseRepository.findAllByBusinessIdOrderByExpenseDateDesc(businessId)
                // findAllByBusinessIdAndExpenseDateBetween has no ORDER BY of its
                // own (listBetween()'s only other caller sums/counts it) — sort
                // explicitly here to keep this list's "most recent first" convention.
                : expenseRepository.findAllByBusinessIdAndExpenseDateBetween(businessId, from, to).stream()
                        .sorted(Comparator.comparing(Expense::getExpenseDate).reversed())
                        .toList();
        return expenses.stream().map(this::toResponse).toList();
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

    @Transactional
    public ExpenseResponse update(UUID id, ExpenseEditRequest req) {
        Expense expense = getOwned(id); // existence/ownership check for everyone, before gating
        if (!approvalGateService.isOwner()) {
            UUID pendingId = approvalGateService.queueForApproval(
                    PendingApproval.SourceType.EXPENSE, PendingApproval.ActionType.EDIT, id, req,
                    "Edit expense (" + expense.getCategory() + ", GH₵" + expense.getAmount()
                            + " → GH₵" + req.expense().amount() + "): " + req.reason());
            throw new ApprovalRequiredException(pendingId, "Expense edit submitted for Owner approval.");
        }
        return doUpdate(expense, req);
    }

    // Called ONLY by PendingApprovalService.approve() — the Owner clicking
    // Approve IS the authorization, so this bypasses the gate entirely.
    @Transactional
    public ExpenseResponse applyApprovedUpdate(UUID id, ExpenseEditRequest req) {
        return doUpdate(getOwned(id), req);
    }

    private ExpenseResponse doUpdate(Expense expense, ExpenseEditRequest req) {
        ExpenseRequest r = req.expense();
        expense.setCategory(r.category());
        expense.setDescription(r.description());
        expense.setPaymentMethod(r.paymentMethod());
        expense.setAmount(r.amount());
        if (r.expenseDate() != null) expense.setExpenseDate(r.expenseDate());
        expense = expenseRepository.save(expense);

        activityLogService.log("Edited expense — " + req.reason(), "EXPENSE", expense.getId());

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
