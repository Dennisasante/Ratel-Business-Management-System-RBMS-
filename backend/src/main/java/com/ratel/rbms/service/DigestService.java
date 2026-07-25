package com.ratel.rbms.service;

import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.Expense;
import com.ratel.rbms.entity.Product;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.ExpenseRepository;
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

/**
 * "Feel safe even when you're away" is the whole point of this — a short,
 * factual email every morning so an Owner never has to wonder what happened
 * yesterday. Deliberately not AI-generated prose: every number here comes
 * straight from a repository query, so what it says is always exactly true.
 */
@Service
public class DigestService {

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final SaleRepository saleRepository;
    private final ExpenseRepository expenseRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final EmailService emailService;

    public DigestService(
            BusinessRepository businessRepository,
            UserRepository userRepository,
            SaleRepository saleRepository,
            ExpenseRepository expenseRepository,
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            EmailService emailService
    ) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.saleRepository = saleRepository;
        this.expenseRepository = expenseRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.emailService = emailService;
    }

    public void sendDailyDigests() {
        // Ghana runs on GMT (UTC+0), so "yesterday in UTC" is correct for the
        // primary market with no timezone field needed. If/when businesses
        // outside that timezone matter, this is the place a per-business
        // timezone would need to plug in.
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);

        for (Business business : businessRepository.findAll()) {
            if (business.isActive()) {
                sendDigestFor(business, yesterday);
            }
        }
    }

    private void sendDigestFor(Business business, LocalDate day) {
        List<User> owners = userRepository.findAllByBusinessIdAndRole(business.getId(), Role.OWNER).stream()
                .filter(User::isActive)
                .toList();
        if (owners.isEmpty()) {
            return;
        }

        Instant fromInstant = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Sale> sales = saleRepository.findAllByBusinessIdAndCreatedAtBetween(business.getId(), fromInstant, toInstant);
        List<Expense> expenses = expenseRepository.findAllByBusinessIdAndExpenseDateBetween(business.getId(), day, day);
        List<Customer> newCustomers = customerRepository.findAllByBusinessIdAndCreatedAtBetween(business.getId(), fromInstant, toInstant);
        List<Product> lowStock = productRepository.findAllByBusinessIdOrderByNameAsc(business.getId()).stream()
                .filter(p -> p.getQuantity() <= p.getLowStockThreshold())
                .toList();

        BigDecimal revenue = sales.stream().map(Sale::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenseTotal = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = revenue.subtract(expenseTotal);

        String dayLabel = day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"));
        String subject = business.getName() + " — daily summary for " + dayLabel;
        String body = buildBody(business, dayLabel, sales.size(), revenue, expenses.size(), expenseTotal, net, newCustomers.size(), lowStock);

        for (User owner : owners) {
            emailService.sendDigest(owner.getEmail(), subject, body);
        }
    }

    private String buildBody(
            Business business, String dayLabel, int salesCount, BigDecimal revenue,
            int expenseCount, BigDecimal expenseTotal, BigDecimal net, int newCustomers, List<Product> lowStock
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Here's what happened at ").append(business.getName()).append(" on ").append(dayLabel).append(".\n\n");

        sb.append("SALES\n");
        if (salesCount == 0) {
            sb.append("No sales recorded.\n\n");
        } else {
            sb.append(salesCount).append(" sale").append(salesCount == 1 ? "" : "s")
                    .append(", GH₵").append(revenue).append(" in revenue.\n\n");
        }

        sb.append("EXPENSES\n");
        if (expenseCount == 0) {
            sb.append("None logged.\n\n");
        } else {
            sb.append(expenseCount).append(" expense").append(expenseCount == 1 ? "" : "s")
                    .append(", GH₵").append(expenseTotal).append(" total.\n\n");
        }

        sb.append("NET FOR THE DAY: GH₵").append(net).append("\n\n");

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
}
