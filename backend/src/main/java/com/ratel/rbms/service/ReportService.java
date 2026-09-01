package com.ratel.rbms.service;

import com.ratel.rbms.dto.ReportSummaryResponse;
import com.ratel.rbms.dto.StaffCommissionResponse;
import com.ratel.rbms.entity.Expense;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.repository.ExpenseRepository;
import com.ratel.rbms.repository.PaymentTransactionRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private final SaleRepository saleRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public ReportService(
            SaleRepository saleRepository,
            ExpenseRepository expenseRepository,
            UserRepository userRepository,
            PaymentTransactionRepository paymentTransactionRepository
    ) {
        this.saleRepository = saleRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    public ReportSummaryResponse summary(LocalDate from, LocalDate to) {
        UUID businessId = TenantContext.getBusinessId();

        var sales = salesInRange(businessId, from, to);
        var expenses = expenseRepository.findAllByBusinessIdAndExpenseDateBetween(businessId, from, to);

        BigDecimal revenue = revenue(businessId, from, to);
        BigDecimal expenseTotal = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ReportSummaryResponse(
                from,
                to,
                revenue,
                expenseTotal,
                revenue.subtract(expenseTotal),
                sales.size(),
                expenses.size()
        );
    }

    /** Per-staff breakdown of sales and the commission they earned (snapshotted per-sale, see Sale.commissionAmount). */
    public List<StaffCommissionResponse> commissions(LocalDate from, LocalDate to) {
        UUID businessId = TenantContext.getBusinessId();
        var sales = salesInRange(businessId, from, to);

        // Group by cashier, preserving encounter order for a stable-ish display order.
        Map<UUID, List<Sale>> byCashier = new LinkedHashMap<>();
        for (Sale sale : sales) {
            if (sale.getCashierId() == null) continue;
            byCashier.computeIfAbsent(sale.getCashierId(), k -> new java.util.ArrayList<>()).add(sale);
        }

        List<StaffCommissionResponse> results = new java.util.ArrayList<>();
        for (Map.Entry<UUID, List<Sale>> entry : byCashier.entrySet()) {
            User user = userRepository.findById(entry.getKey()).orElse(null);
            if (user == null) continue;

            BigDecimal totalSales = entry.getValue().stream().map(Sale::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal commissionEarned = entry.getValue().stream().map(Sale::getCommissionAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            results.add(new StaffCommissionResponse(
                    user.getId(), user.getFullName(), user.getCommissionRate(),
                    entry.getValue().size(), totalSales, commissionEarned
            ));
        }

        results.sort((a, b) -> b.commissionEarned().compareTo(a.commissionEarned()));
        return results;
    }

    /**
     * Money actually retained in this range, from every revenue source (Sale,
     * ServiceOrder, Booking, PurchaseOrder, CustomWigRequest, or anything
     * added later) — net of any refunds paid back out on those same source
     * types. A PURCHASE_ORDER outgoing payment (money paid to a supplier) is
     * a completely different kind of transaction and is never netted against
     * revenue here. This is THE single revenue formula — DashboardService
     * calls this too, never its own copy, so Dashboard and Reports can never
     * disagree (see PaymentTransactionRepository.sumAmount/sumRefunds).
     */
    public BigDecimal revenue(UUID businessId, LocalDate from, LocalDate to) {
        var fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        var toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal collected = paymentTransactionRepository.sumAmount(
                businessId, PaymentTransaction.Direction.INCOMING, "SUCCESS", fromInstant, toInstant
        );
        BigDecimal refunded = paymentTransactionRepository.sumRefunds(
                businessId, PaymentTransaction.Direction.OUTGOING, "SUCCESS",
                PaymentTransaction.SourceType.PURCHASE_ORDER, fromInstant, toInstant
        );
        return collected.subtract(refunded);
    }

    private List<Sale> salesInRange(UUID businessId, LocalDate from, LocalDate to) {
        // Sales are timestamped (Instant); expenses/dates elsewhere are plain calendar
        // dates. Treat the range as the whole of `from` through the whole of `to`, in UTC.
        var fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        var toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return saleRepository.findAllByBusinessIdAndCreatedAtBetween(businessId, fromInstant, toInstant);
    }
}
