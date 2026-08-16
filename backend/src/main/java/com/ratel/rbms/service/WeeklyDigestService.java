package com.ratel.rbms.service;

import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.CustomWigRequest;
import com.ratel.rbms.entity.Expense;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.entity.PlatformAdmin;
import com.ratel.rbms.entity.Product;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.BillingStatus;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomWigRequestRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.ExpenseRepository;
import com.ratel.rbms.repository.HelpRequestRepository;
import com.ratel.rbms.repository.PaymentTransactionRepository;
import com.ratel.rbms.repository.PlatformAdminRepository;
import com.ratel.rbms.repository.ProductRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * The weekly counterpart to {@link DigestService} — same "always exactly
 * true, never AI-generated" philosophy, just a 7-day window instead of 1,
 * plus a second audience: the Super Admin gets a platform-wide rollup that
 * no Owner-facing digest could ever cover.
 */
@Service
public class WeeklyDigestService {

    private static final DateTimeFormatter RANGE_FORMAT = DateTimeFormatter.ofPattern("MMM d");

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final SaleRepository saleRepository;
    private final ExpenseRepository expenseRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final BookingRepository bookingRepository;
    private final CustomWigRequestRepository customWigRequestRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final EmailService emailService;

    public WeeklyDigestService(
            BusinessRepository businessRepository,
            UserRepository userRepository,
            SaleRepository saleRepository,
            ExpenseRepository expenseRepository,
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            BookingRepository bookingRepository,
            CustomWigRequestRepository customWigRequestRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            HelpRequestRepository helpRequestRepository,
            PlatformAdminRepository platformAdminRepository,
            EmailService emailService
    ) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.saleRepository = saleRepository;
        this.expenseRepository = expenseRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.bookingRepository = bookingRepository;
        this.customWigRequestRepository = customWigRequestRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.helpRequestRepository = helpRequestRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.emailService = emailService;
    }

    // ---- Owners --------------------------------------------------------

    public void sendOwnerWeeklyDigests() {
        LocalDate weekEnd = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        LocalDate weekStart = weekEnd.minusDays(6);

        for (Business business : businessRepository.findAll()) {
            if (business.isActive()) {
                sendOwnerDigestFor(business, weekStart, weekEnd);
            }
        }
    }

    private void sendOwnerDigestFor(Business business, LocalDate weekStart, LocalDate weekEnd) {
        List<User> owners = userRepository.findAllByBusinessIdAndRole(business.getId(), Role.OWNER).stream()
                .filter(User::isActive)
                .toList();
        if (owners.isEmpty()) {
            return;
        }

        Instant fromInstant = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = weekEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Sale> sales = saleRepository.findAllByBusinessIdAndCreatedAtBetween(business.getId(), fromInstant, toInstant);
        List<Expense> expenses = expenseRepository.findAllByBusinessIdAndExpenseDateBetween(business.getId(), weekStart, weekEnd);
        List<Customer> newCustomers = customerRepository.findAllByBusinessIdAndCreatedAtBetween(business.getId(), fromInstant, toInstant);
        List<Booking> newBookings = bookingRepository.findAllByBusinessIdAndCreatedAtBetween(business.getId(), fromInstant, toInstant);
        List<CustomWigRequest> newWigRequests = customWigRequestRepository.findAllByBusinessIdAndCreatedAtBetween(business.getId(), fromInstant, toInstant);
        List<Product> lowStock = productRepository.findAllByBusinessIdOrderByNameAsc(business.getId()).stream()
                .filter(p -> p.getQuantity() <= p.getLowStockThreshold())
                .toList();

        BigDecimal revenue = sales.stream().map(Sale::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenseTotal = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = revenue.subtract(expenseTotal);

        String rangeLabel = weekStart.format(RANGE_FORMAT) + " – " + weekEnd.format(RANGE_FORMAT);
        String subject = business.getName() + " — weekly summary for " + rangeLabel;
        String body = buildOwnerBody(business, rangeLabel, sales.size(), revenue, expenses.size(), expenseTotal, net,
                newCustomers.size(), newBookings.size(), newWigRequests.size(), lowStock);

        for (User owner : owners) {
            emailService.sendDigest(owner.getEmail(), subject, body);
        }
    }

    private String buildOwnerBody(
            Business business, String rangeLabel, int salesCount, BigDecimal revenue,
            int expenseCount, BigDecimal expenseTotal, BigDecimal net, int newCustomers,
            int newBookings, int newWigRequests, List<Product> lowStock
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Here's your week at ").append(business.getName()).append(" (").append(rangeLabel).append(").\n\n");

        sb.append("SALES\n");
        if (salesCount == 0) {
            sb.append("No sales recorded.\n\n");
        } else {
            sb.append(salesCount).append(" sale").append(salesCount == 1 ? "" : "s")
                    .append(", GH₵").append(revenue).append(" in revenue.\n\n");
        }

        sb.append("BOOKINGS\n");
        sb.append(newBookings == 0 ? "None this week.\n\n"
                : newBookings + " new booking" + (newBookings == 1 ? "" : "s") + ".\n\n");

        sb.append("CUSTOM WIG REQUESTS\n");
        sb.append(newWigRequests == 0 ? "None this week.\n\n"
                : newWigRequests + " new request" + (newWigRequests == 1 ? "" : "s") + ".\n\n");

        sb.append("EXPENSES\n");
        if (expenseCount == 0) {
            sb.append("None logged.\n\n");
        } else {
            sb.append(expenseCount).append(" expense").append(expenseCount == 1 ? "" : "s")
                    .append(", GH₵").append(expenseTotal).append(" total.\n\n");
        }

        sb.append("NET FOR THE WEEK: GH₵").append(net).append("\n\n");

        if (newCustomers > 0) {
            sb.append("NEW CUSTOMERS\n").append(newCustomers).append(" added.\n\n");
        }

        sb.append("STOCK\n");
        if (lowStock.isEmpty()) {
            sb.append("Everything's above its low-stock threshold.\n");
        } else {
            sb.append(lowStock.size()).append(" product").append(lowStock.size() == 1 ? "" : "s").append(" running low:\n");
            for (Product p : lowStock) {
                sb.append("  - ").append(p.getName()).append(" (").append(p.getQuantity()).append(" left)\n");
            }
        }

        return sb.toString();
    }

    // ---- Super Admin -----------------------------------------------------

    public void sendSuperAdminWeeklyDigest() {
        List<PlatformAdmin> admins = platformAdminRepository.findAll();
        if (admins.isEmpty()) {
            return;
        }

        LocalDate weekEnd = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        LocalDate weekStart = weekEnd.minusDays(6);
        Instant fromInstant = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = weekEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Business> newBusinesses = businessRepository.findAllByCreatedAtAfter(fromInstant).stream()
                .filter(b -> b.getCreatedAt().isBefore(toInstant))
                .toList();

        BigDecimal weekRevenue = paymentTransactionRepository.sumAmountByDateRange(
                PaymentTransaction.Direction.INCOMING, "SUCCESS", fromInstant, toInstant);

        Map<BillingStatus, Long> billingCounts = businessRepository.countGroupedByBillingStatus().stream()
                .collect(java.util.stream.Collectors.toMap(
                        BusinessRepository.BillingStatusCount::getBillingStatus,
                        BusinessRepository.BillingStatusCount::getTotal));
        long trialing = billingCounts.getOrDefault(BillingStatus.TRIALING, 0L);
        long activeBusinesses = businessRepository.countByActive(true);
        long suspendedBusinesses = businessRepository.countByActive(false);

        long newOpenHelpRequests = helpRequestRepository.countByStatusAndCreatedAtBetween("OPEN", fromInstant, toInstant);

        String rangeLabel = weekStart.format(RANGE_FORMAT) + " – " + weekEnd.format(RANGE_FORMAT);
        String subject = "Tallia platform — weekly summary for " + rangeLabel;
        String body = buildSuperAdminBody(rangeLabel, newBusinesses, weekRevenue, activeBusinesses, trialing,
                suspendedBusinesses, newOpenHelpRequests);

        for (PlatformAdmin admin : admins) {
            emailService.sendDigest(admin.getEmail(), subject, body);
        }
    }

    private String buildSuperAdminBody(
            String rangeLabel, List<Business> newBusinesses, BigDecimal weekRevenue,
            long active, long trialing, long suspended, long newOpenHelpRequests
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Platform summary for ").append(rangeLabel).append(".\n\n");

        sb.append("NEW BUSINESSES\n");
        if (newBusinesses.isEmpty()) {
            sb.append("None this week.\n\n");
        } else {
            sb.append(newBusinesses.size()).append(" signed up:\n");
            for (Business b : newBusinesses) {
                sb.append("  - ").append(b.getName()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("REVENUE\n");
        sb.append("GH₵").append(weekRevenue).append(" collected across all businesses this week (payment ledger).\n\n");

        sb.append("BUSINESSES\n");
        sb.append(active).append(" active, ").append(trialing).append(" trialing, ")
                .append(suspended).append(" suspended.\n\n");

        sb.append("HELP REQUESTS\n");
        sb.append(newOpenHelpRequests == 0
                ? "None new this week — all caught up.\n"
                : newOpenHelpRequests + " new this week still awaiting a response.\n");

        return sb.toString();
    }
}
