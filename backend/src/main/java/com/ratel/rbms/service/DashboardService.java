package com.ratel.rbms.service;

import com.ratel.rbms.dto.DashboardAttentionResponse;
import com.ratel.rbms.dto.DashboardChartResponse;
import com.ratel.rbms.dto.DashboardSummaryResponse;
import com.ratel.rbms.dto.InventorySnapshotResponse;
import com.ratel.rbms.dto.ProductsNeedingAttentionResponse;
import com.ratel.rbms.dto.SalesBreakdownResponse;
import com.ratel.rbms.dto.TopProductResponse;
import com.ratel.rbms.entity.BusinessIntegrations;
import com.ratel.rbms.entity.CustomWigRequest;
import com.ratel.rbms.entity.EcommerceOrder;
import com.ratel.rbms.entity.Expense;
import com.ratel.rbms.entity.Invoice;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.entity.Product;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.SaleItem;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.SaleItemType;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.repository.BusinessIntegrationsRepository;
import com.ratel.rbms.repository.CustomWigRequestRepository;
import com.ratel.rbms.repository.EcommerceOrderRepository;
import com.ratel.rbms.repository.ExpenseRepository;
import com.ratel.rbms.repository.InvoiceRepository;
import com.ratel.rbms.repository.PaymentTransactionRepository;
import com.ratel.rbms.repository.PendingApprovalRepository;
import com.ratel.rbms.repository.ProductRepository;
import com.ratel.rbms.repository.SaleItemRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import com.ratel.rbms.util.ProfitCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backs the restructured Dashboard. Deliberately reuses the exact same
 * revenue/expense source data as ReportService (PaymentTransaction for money
 * actually collected, Expense for spend) rather than a second formula, so
 * Dashboard and Reports can never disagree — see ProfitCalculator for the
 * shared profit/margin math on top of that.
 *
 * COGS/gross profit is computed separately from PRODUCT sale line items
 * only — Service Orders, Bookings, Custom Wig Requests, and E-commerce
 * Orders have no inventory cost concept and never get one invented for them
 * here; their revenue still counts toward the top-line Revenue figure, it
 * just carries no offsetting COGS (i.e. it flows straight through to gross
 * profit, which is correct — a service has no cost of goods).
 */
@Service
public class DashboardService {

    private static final BigDecimal DEFAULT_MIN_MARGIN_PERCENT = new BigDecimal("15.00");

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final ProductRepository productRepository;
    private final CustomWigRequestRepository customWigRequestRepository;
    private final EcommerceOrderRepository ecommerceOrderRepository;
    private final PendingApprovalRepository pendingApprovalRepository;
    private final InvoiceRepository invoiceRepository;
    private final BusinessIntegrationsRepository businessIntegrationsRepository;
    private final PlanFeatureService planFeatureService;

    public DashboardService(
            SaleRepository saleRepository,
            SaleItemRepository saleItemRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            ExpenseRepository expenseRepository,
            UserRepository userRepository,
            ServiceOrderRepository serviceOrderRepository,
            ProductRepository productRepository,
            CustomWigRequestRepository customWigRequestRepository,
            EcommerceOrderRepository ecommerceOrderRepository,
            PendingApprovalRepository pendingApprovalRepository,
            InvoiceRepository invoiceRepository,
            BusinessIntegrationsRepository businessIntegrationsRepository,
            PlanFeatureService planFeatureService
    ) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.productRepository = productRepository;
        this.customWigRequestRepository = customWigRequestRepository;
        this.ecommerceOrderRepository = ecommerceOrderRepository;
        this.pendingApprovalRepository = pendingApprovalRepository;
        this.invoiceRepository = invoiceRepository;
        this.businessIntegrationsRepository = businessIntegrationsRepository;
        this.planFeatureService = planFeatureService;
    }

    // ---------------------------------------------------------------
    // Business Overview
    // ---------------------------------------------------------------

    public DashboardSummaryResponse summary(LocalDate from, LocalDate to) {
        UUID businessId = TenantContext.getBusinessId();

        LocalDate effFrom = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate effTo = to != null ? to : LocalDate.now();
        PeriodMetrics current = computeMetrics(businessId, effFrom, effTo);

        // A period-over-period comparison only makes sense for a bounded
        // range — "All" (both null) has no equivalent "previous" window.
        PeriodMetrics previous = null;
        if (from != null && to != null) {
            long days = ChronoUnit.DAYS.between(from, to) + 1;
            LocalDate prevTo = from.minusDays(1);
            LocalDate prevFrom = prevTo.minusDays(days - 1);
            previous = computeMetrics(businessId, prevFrom, prevTo);
        }

        int teamMembers = userRepository.findAllByBusinessId(businessId).size();
        int activeServiceOrders = countActiveServiceOrders(businessId);

        return new DashboardSummaryResponse(
                effFrom, effTo,
                current.revenue, previous != null ? previous.revenue : null,
                current.cogs,
                current.grossProfit, previous != null ? previous.grossProfit : null,
                current.grossMarginPercent, previous != null ? previous.grossMarginPercent : null,
                current.expenses, previous != null ? previous.expenses : null,
                current.netProfit, previous != null ? previous.netProfit : null,
                teamMembers,
                activeServiceOrders
        );
    }

    private int countActiveServiceOrders(UUID businessId) {
        return (int) serviceOrderRepository.findAllByBusinessIdOrderByReceivedAtDesc(businessId).stream()
                .filter(o -> o.getStatus() == ServiceOrderStatus.RECEIVED || o.getStatus() == ServiceOrderStatus.IN_PROGRESS)
                .count();
    }

    private record PeriodMetrics(
            BigDecimal revenue, BigDecimal cogs, BigDecimal grossProfit,
            BigDecimal grossMarginPercent, BigDecimal expenses, BigDecimal netProfit
    ) {
    }

    private PeriodMetrics computeMetrics(UUID businessId, LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Actual money collected, from every source — identical to
        // ReportService.summary()'s own revenue figure.
        BigDecimal revenue = paymentTransactionRepository.sumAmount(
                businessId, PaymentTransaction.Direction.INCOMING, "SUCCESS", fromInstant, toInstant
        );

        List<Sale> sales = nonFailedSalesInRange(businessId, fromInstant, toInstant);
        List<SaleItem> items = productItemsFor(sales);
        BigDecimal cogs = items.stream()
                .filter(i -> i.getUnitCost() != null)
                .map(i -> i.getUnitCost().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grossProfit = ProfitCalculator.profit(revenue, cogs);
        BigDecimal grossMarginPercent = ProfitCalculator.marginPercent(grossProfit, revenue);

        List<Expense> expenses = expenseRepository.findAllByBusinessIdAndExpenseDateBetween(businessId, from, to);
        BigDecimal expenseTotal = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfit = grossProfit != null ? grossProfit.subtract(expenseTotal) : null;

        return new PeriodMetrics(revenue, cogs, grossProfit, grossMarginPercent, expenseTotal, netProfit);
    }

    // A sale whose online payment failed never actually collected any money —
    // it's already excluded from `revenue` above via the SUCCESS filter on
    // PaymentTransaction, so its cost is excluded here too rather than
    // distorting COGS/gross profit against revenue that was never realized.
    private List<Sale> nonFailedSalesInRange(UUID businessId, Instant from, Instant to) {
        return saleRepository.findAllByBusinessIdAndCreatedAtBetween(businessId, from, to).stream()
                .filter(s -> !"FAILED".equals(s.getPaymentStatus()))
                .toList();
    }

    private List<SaleItem> productItemsFor(List<Sale> sales) {
        if (sales.isEmpty()) return List.of();
        List<UUID> saleIds = sales.stream().map(Sale::getId).toList();
        return saleItemRepository.findAllBySaleIdIn(saleIds).stream()
                .filter(i -> i.getItemType() == SaleItemType.PRODUCT)
                .toList();
    }

    // ---------------------------------------------------------------
    // Sales & Profit chart
    // ---------------------------------------------------------------

    public DashboardChartResponse chart(LocalDate from, LocalDate to) {
        UUID businessId = TenantContext.getBusinessId();
        LocalDate effFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effTo = to != null ? to : LocalDate.now();

        long totalDays = ChronoUnit.DAYS.between(effFrom, effTo) + 1;
        String granularity = totalDays <= 62 ? "DAY" : totalDays <= 182 ? "WEEK" : "MONTH";

        Instant fromInstant = effFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = effTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<PaymentTransaction> transactions = paymentTransactionRepository
                .findAllByBusinessIdAndDirectionAndStatusAndCreatedAtBetween(
                        businessId, PaymentTransaction.Direction.INCOMING, "SUCCESS", fromInstant, toInstant);
        List<Sale> sales = nonFailedSalesInRange(businessId, fromInstant, toInstant);
        List<SaleItem> items = productItemsFor(sales);
        Map<UUID, List<SaleItem>> itemsBySaleId = new LinkedHashMap<>();
        for (SaleItem item : items) {
            itemsBySaleId.computeIfAbsent(item.getSaleId(), k -> new ArrayList<>()).add(item);
        }

        // Bucket key = the bucket's own start date (UTC calendar day/week/month).
        Map<LocalDate, BigDecimal> revenueByBucket = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> cogsByBucket = new LinkedHashMap<>();
        Map<LocalDate, Integer> ordersByBucket = new LinkedHashMap<>();

        for (PaymentTransaction t : transactions) {
            LocalDate bucket = bucketStart(t.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(), granularity);
            revenueByBucket.merge(bucket, t.getAmount(), BigDecimal::add);
        }
        for (Sale sale : sales) {
            LocalDate bucket = bucketStart(sale.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(), granularity);
            ordersByBucket.merge(bucket, 1, Integer::sum);
            BigDecimal saleCogs = itemsBySaleId.getOrDefault(sale.getId(), List.of()).stream()
                    .filter(i -> i.getUnitCost() != null)
                    .map(i -> i.getUnitCost().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            cogsByBucket.merge(bucket, saleCogs, BigDecimal::add);
        }

        List<LocalDate> bucketStarts = new ArrayList<>();
        LocalDate cursor = bucketStart(effFrom, granularity);
        LocalDate endBucket = bucketStart(effTo, granularity);
        while (!cursor.isAfter(endBucket)) {
            bucketStarts.add(cursor);
            cursor = switch (granularity) {
                case "WEEK" -> cursor.plusWeeks(1);
                case "MONTH" -> cursor.plusMonths(1);
                default -> cursor.plusDays(1);
            };
        }

        List<DashboardChartResponse.Point> points = new ArrayList<>();
        for (LocalDate b : bucketStarts) {
            BigDecimal revenue = revenueByBucket.getOrDefault(b, BigDecimal.ZERO);
            BigDecimal cogs = cogsByBucket.getOrDefault(b, BigDecimal.ZERO);
            BigDecimal grossProfit = revenue.subtract(cogs);
            int orders = ordersByBucket.getOrDefault(b, 0);
            points.add(new DashboardChartResponse.Point(b, chartLabel(b, granularity), revenue, grossProfit, orders));
        }

        return new DashboardChartResponse(granularity, points);
    }

    private LocalDate bucketStart(LocalDate date, String granularity) {
        return switch (granularity) {
            case "WEEK" -> date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case "MONTH" -> date.withDayOfMonth(1);
            default -> date;
        };
    }

    private String chartLabel(LocalDate bucketStart, String granularity) {
        return switch (granularity) {
            case "WEEK" -> bucketStart.getMonthValue() + "/" + bucketStart.getDayOfMonth();
            case "MONTH" -> bucketStart.getMonth().toString().substring(0, 3) + " " + bucketStart.getYear();
            default -> bucketStart.getMonthValue() + "/" + bucketStart.getDayOfMonth();
        };
    }

    // ---------------------------------------------------------------
    // Needs your attention
    // ---------------------------------------------------------------

    public DashboardAttentionResponse attention() {
        UUID businessId = TenantContext.getBusinessId();

        List<Product> activeProducts = productRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .filter(Product::isActive)
                .toList();
        int lowStock = (int) activeProducts.stream().filter(p -> p.getQuantity() > 0 && p.getQuantity() <= p.getLowStockThreshold()).count();
        int outOfStock = (int) activeProducts.stream().filter(p -> p.getQuantity() <= 0).count();
        BigDecimal minMargin = minProfitMarginPercent(businessId);
        int lowMargin = (int) activeProducts.stream().filter(p -> isLowMargin(p, minMargin)).count();

        boolean customWigEnabled = planFeatureService.hasFeature(businessId, PlanFeature.CUSTOM_WIG_REQUESTS);
        int newCustomWig = customWigEnabled
                ? (int) customWigRequestRepository.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .filter(r -> "SUBMITTED".equals(r.getStatus())).count()
                : 0;

        boolean ecommerceEnabled = planFeatureService.hasFeature(businessId, PlanFeature.WOOCOMMERCE_SYNC);
        int ecommerceToFulfill = ecommerceEnabled
                ? (int) ecommerceOrderRepository.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .filter(o -> "RECEIVED".equals(o.getStatus()) || "PROCESSING".equals(o.getStatus())).count()
                : 0;

        int pendingApprovals = pendingApprovalRepository
                .findAllByBusinessIdAndStatusOrderByRequestedAtDesc(businessId, com.ratel.rbms.entity.PendingApproval.Status.PENDING)
                .size();

        LocalDate today = LocalDate.now();
        int overdueInvoices = (int) invoiceRepository.findAllByBusinessIdOrderByIssueDateDesc(businessId).stream()
                .filter(inv -> !"PAID".equals(inv.getStatus()) && inv.getDueDate() != null && inv.getDueDate().isBefore(today))
                .count();

        return new DashboardAttentionResponse(
                lowStock, outOfStock, lowMargin,
                customWigEnabled, newCustomWig,
                ecommerceEnabled, ecommerceToFulfill,
                pendingApprovals, overdueInvoices
        );
    }

    private BigDecimal minProfitMarginPercent(UUID businessId) {
        return businessIntegrationsRepository.findByBusinessId(businessId)
                .map(BusinessIntegrations::getMinProfitMarginPercent)
                .orElse(DEFAULT_MIN_MARGIN_PERCENT);
    }

    private boolean isLowMargin(Product p, BigDecimal minMargin) {
        BigDecimal profit = ProfitCalculator.profit(p.getSellingPrice(), p.getCostPrice());
        BigDecimal margin = ProfitCalculator.marginPercent(profit, p.getSellingPrice());
        return margin != null && margin.compareTo(minMargin) < 0;
    }

    // ---------------------------------------------------------------
    // Top Products
    // ---------------------------------------------------------------

    public enum TopProductRankMetric { REVENUE, UNITS_SOLD, GROSS_PROFIT, MARGIN }

    public List<TopProductResponse> topProducts(LocalDate from, LocalDate to, TopProductRankMetric rankBy, int limit) {
        UUID businessId = TenantContext.getBusinessId();
        LocalDate effFrom = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate effTo = to != null ? to : LocalDate.now();
        Instant fromInstant = effFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = effTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Sale> sales = nonFailedSalesInRange(businessId, fromInstant, toInstant);
        List<SaleItem> items = productItemsFor(sales);

        Map<UUID, ProductAgg> byProduct = new LinkedHashMap<>();
        for (SaleItem item : items) {
            if (item.getProductId() == null) continue;
            ProductAgg agg = byProduct.computeIfAbsent(item.getProductId(), k -> new ProductAgg(item.getProductName()));
            agg.unitsSold += item.getQuantity();
            agg.revenue = agg.revenue.add(item.getSubtotal());
            if (item.getUnitCost() != null) {
                agg.cogs = agg.cogs.add(item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity())));
                agg.hasCost = true;
            }
        }

        List<TopProductResponse> results = new ArrayList<>();
        for (Map.Entry<UUID, ProductAgg> entry : byProduct.entrySet()) {
            ProductAgg agg = entry.getValue();
            BigDecimal grossProfit = agg.hasCost ? ProfitCalculator.profit(agg.revenue, agg.cogs) : null;
            BigDecimal marginPercent = agg.hasCost ? ProfitCalculator.marginPercent(grossProfit, agg.revenue) : null;
            results.add(new TopProductResponse(entry.getKey(), agg.productName, agg.unitsSold, agg.revenue, grossProfit, marginPercent));
        }

        Comparator<TopProductResponse> comparator = switch (rankBy) {
            case UNITS_SOLD -> Comparator.comparingInt(TopProductResponse::unitsSold);
            case GROSS_PROFIT -> Comparator.comparing(TopProductResponse::grossProfit, Comparator.nullsFirst(Comparator.naturalOrder()));
            case MARGIN -> Comparator.comparing(TopProductResponse::grossMarginPercent, Comparator.nullsFirst(Comparator.naturalOrder()));
            default -> Comparator.comparing(TopProductResponse::revenue);
        };
        results.sort(comparator.reversed());

        return results.stream().limit(Math.max(1, limit)).toList();
    }

    private static final class ProductAgg {
        final String productName;
        int unitsSold = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        boolean hasCost = false;

        ProductAgg(String productName) {
            this.productName = productName;
        }
    }

    // ---------------------------------------------------------------
    // Products Needing Attention
    // ---------------------------------------------------------------

    public ProductsNeedingAttentionResponse productsNeedingAttention() {
        UUID businessId = TenantContext.getBusinessId();
        List<Product> activeProducts = productRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .filter(Product::isActive)
                .toList();
        BigDecimal minMargin = minProfitMarginPercent(businessId);

        List<ProductsNeedingAttentionResponse.Item> lowStock = activeProducts.stream()
                .filter(p -> p.getQuantity() > 0 && p.getQuantity() <= p.getLowStockThreshold())
                .sorted(Comparator.comparingInt(Product::getQuantity))
                .map(p -> new ProductsNeedingAttentionResponse.Item(p.getId(), p.getName(), p.getQuantity(), p.getLowStockThreshold(), null))
                .toList();

        List<ProductsNeedingAttentionResponse.Item> outOfStock = activeProducts.stream()
                .filter(p -> p.getQuantity() <= 0)
                .map(p -> new ProductsNeedingAttentionResponse.Item(p.getId(), p.getName(), p.getQuantity(), p.getLowStockThreshold(), null))
                .toList();

        List<ProductsNeedingAttentionResponse.Item> lowMargin = activeProducts.stream()
                .filter(p -> isLowMargin(p, minMargin))
                .map(p -> {
                    BigDecimal profit = ProfitCalculator.profit(p.getSellingPrice(), p.getCostPrice());
                    BigDecimal margin = ProfitCalculator.marginPercent(profit, p.getSellingPrice());
                    return new ProductsNeedingAttentionResponse.Item(p.getId(), p.getName(), p.getQuantity(), p.getLowStockThreshold(), margin);
                })
                .sorted(Comparator.comparing(ProductsNeedingAttentionResponse.Item::profitMarginPercent, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new ProductsNeedingAttentionResponse(lowStock, outOfStock, lowMargin);
    }

    // ---------------------------------------------------------------
    // Inventory Snapshot
    // ---------------------------------------------------------------

    public InventorySnapshotResponse inventorySnapshot() {
        UUID businessId = TenantContext.getBusinessId();
        List<Product> activeProducts = productRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .filter(Product::isActive)
                .toList();

        int totalProducts = activeProducts.size();
        int totalQuantity = activeProducts.stream().mapToInt(Product::getQuantity).sum();
        int lowStockCount = (int) activeProducts.stream().filter(p -> p.getQuantity() > 0 && p.getQuantity() <= p.getLowStockThreshold()).count();
        int outOfStockCount = (int) activeProducts.stream().filter(p -> p.getQuantity() <= 0).count();
        BigDecimal inventoryValue = activeProducts.stream()
                .map(p -> p.getCostPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<InventorySnapshotResponse.LowStockItem> lowStockItems = activeProducts.stream()
                .filter(p -> p.getQuantity() <= p.getLowStockThreshold())
                .sorted(Comparator.comparingInt(Product::getQuantity))
                .limit(8)
                .map(p -> new InventorySnapshotResponse.LowStockItem(p.getId(), p.getName(), p.getQuantity(), p.getLowStockThreshold()))
                .toList();

        return new InventorySnapshotResponse(totalProducts, totalQuantity, lowStockCount, outOfStockCount, inventoryValue, lowStockItems);
    }

    // ---------------------------------------------------------------
    // Sales Breakdown
    // ---------------------------------------------------------------

    public enum SalesBreakdownDimension { PAYMENT_METHOD, CATEGORY, SALESPERSON }

    public SalesBreakdownResponse salesBreakdown(LocalDate from, LocalDate to, SalesBreakdownDimension dimension) {
        UUID businessId = TenantContext.getBusinessId();
        LocalDate effFrom = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate effTo = to != null ? to : LocalDate.now();
        Instant fromInstant = effFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = effTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Sale> sales = nonFailedSalesInRange(businessId, fromInstant, toInstant);

        Map<String, BigDecimal> byLabel = new LinkedHashMap<>();

        switch (dimension) {
            case PAYMENT_METHOD -> {
                for (Sale s : sales) {
                    byLabel.merge(s.getPaymentMethod().toString(), s.getAmountPaid(), BigDecimal::add);
                }
            }
            case SALESPERSON -> {
                Map<UUID, String> nameCache = new LinkedHashMap<>();
                for (Sale s : sales) {
                    if (s.getCashierId() == null) continue;
                    String name = nameCache.computeIfAbsent(s.getCashierId(), id ->
                            userRepository.findById(id).map(User::getFullName).orElse("Unknown"));
                    byLabel.merge(name, s.getAmountPaid(), BigDecimal::add);
                }
            }
            case CATEGORY -> {
                // Category isn't snapshotted on SaleItem (unlike unitPrice/unitCost),
                // so this is a best-effort lookup against each product's CURRENT
                // category rather than a true point-in-time value — reasonable for
                // a breakdown chart, unlike the sale-level profit figures above
                // which must never drift from what was actually recorded.
                List<SaleItem> items = productItemsFor(sales);
                Map<UUID, String> categoryCache = new LinkedHashMap<>();
                for (SaleItem item : items) {
                    if (item.getProductId() == null) continue;
                    String category = categoryCache.computeIfAbsent(item.getProductId(), id ->
                            productRepository.findByIdAndBusinessId(id, businessId)
                                    .map(p -> p.getCategory() == null || p.getCategory().isBlank() ? "Uncategorized" : p.getCategory())
                                    .orElse("Uncategorized"));
                    byLabel.merge(category, item.getSubtotal(), BigDecimal::add);
                }
            }
        }

        BigDecimal total = byLabel.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SalesBreakdownResponse.Entry> entries = byLabel.entrySet().stream()
                .map(e -> new SalesBreakdownResponse.Entry(
                        e.getKey(), e.getValue(), ProfitCalculator.marginPercent(e.getValue(), total)))
                .sorted(Comparator.comparing(SalesBreakdownResponse.Entry::revenue).reversed())
                .toList();

        return new SalesBreakdownResponse(dimension.toString(), entries);
    }
}
