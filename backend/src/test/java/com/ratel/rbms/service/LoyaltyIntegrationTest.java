package com.ratel.rbms.service;

import com.ratel.rbms.dto.LoyaltyProgramRequest;
import com.ratel.rbms.dto.LoyaltyStatusResponse;
import com.ratel.rbms.dto.PublicLoyaltyResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.SaleItem;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.entity.enums.PaymentMethod;
import com.ratel.rbms.entity.enums.SaleItemType;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.SaleItemRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real PostgreSQL, real concurrent connections. The promises to a business: a customer earns at
 * most ONE visit a day however many sales or paths race, a reward can be given exactly as many
 * times as it was earned, and none of it can ever touch the sale itself.
 */
@SpringBootTest
class LoyaltyIntegrationTest {

    @Autowired private LoyaltyService loyaltyService;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleItemRepository saleItemRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceCatalogItemRepository serviceCatalogItemRepository;
    @Autowired private ServiceOrderRepository serviceOrderRepository;
    @Autowired private JdbcTemplate jdbc;

    private UUID businessId;
    private UUID customerId;
    private UUID serviceTypeId;
    private UUID countingService;
    private UUID nonCountingService;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        businessId = businessRepository.save(Business.builder()
                .name("Shell Luxury Hair").slug("shell-" + unique).industry(Industry.SALON).currency("GHS")
                .enabledModules(List.of("INVENTORY", "SALES", "CUSTOMERS", "EXPENSES", "LOYALTY", "BOOKINGS"))
                .build()).getId();
        customerId = customerRepository.save(Customer.builder()
                .businessId(businessId).fullName("Dennis Asante").phone("0244112233").build()).getId();
        serviceTypeId = serviceTypeRepository.save(ServiceType.builder().businessId(businessId).name("Hair").build()).getId();
        countingService = serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(businessId).serviceTypeId(serviceTypeId).name("Wig install").price(new BigDecimal("450")).build()).getId();
        nonCountingService = serviceCatalogItemRepository.save(ServiceCatalogItem.builder()
                .businessId(businessId).serviceTypeId(serviceTypeId).name("Top-up").price(new BigDecimal("20"))
                .countsForLoyalty(false).build()).getId();
        TenantContext.setBusinessId(businessId);
        TenantContext.setUserId(null);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbc.update("DELETE FROM loyalty_redemptions WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM loyalty_visits WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM loyalty_customers WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM loyalty_programs WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM activity_logs WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM service_orders WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM sale_items WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM sales WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM service_catalog WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM service_types WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM customers WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM businesses WHERE id = ?", businessId);
    }

    @Test
    void aPaidSaleWithACountingServiceEarnsAVisitAndAPersonalLink() {
        UUID sale = serviceSale(countingService, "PAID");

        assertTrue(loyaltyService.recordVisitForSale(sale));

        LoyaltyStatusResponse status = loyaltyService.statusFor(businessId, customerId);
        assertEquals(1, status.visits());
        assertEquals(10, status.target());
        assertFalse(status.rewardDue());
        assertTrue(status.pointsUrl().matches(".*/p/[a-z2-9]{10}$"), status.pointsUrl());
    }

    @Test
    void aCustomerEarnsAtMostOneVisitADayEvenWhenManySalesRaceAtOnce() throws Exception {
        List<UUID> sales = new ArrayList<>();
        for (int i = 0; i < 12; i++) sales.add(serviceSale(countingService, "PAID"));

        ExecutorService pool = Executors.newFixedThreadPool(sales.size());
        CountDownLatch ready = new CountDownLatch(sales.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (UUID sale : sales) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return loyaltyService.recordVisitForSale(sale);
            }));
        }
        ready.await();
        go.countDown();
        int counted = 0;
        for (Future<Boolean> f : results) if (f.get()) counted++;
        pool.shutdown();

        assertEquals(1, counted, "exactly one of the twelve sales counted");
        assertEquals(1, loyaltyService.statusFor(businessId, customerId).visits());
        assertEquals(1, count("SELECT count(*) FROM loyalty_visits WHERE business_id = ?"));
    }

    @Test
    void aSaleOfOnlyProductsOrAServiceTheOwnerSwitchedOffDoesNotCount() {
        UUID productOnly = sale("PAID", SaleItemType.PRODUCT, null);
        UUID nonCounting = serviceSale(nonCountingService, "PAID");

        assertFalse(loyaltyService.recordVisitForSale(productOnly));
        assertFalse(loyaltyService.recordVisitForSale(nonCounting));
        assertEquals(0, count("SELECT count(*) FROM loyalty_visits WHERE business_id = ?"));
    }

    @Test
    void anUnpaidSaleAndAWalkInDoNotCount() {
        assertFalse(loyaltyService.recordVisitForSale(serviceSale(countingService, "UNPAID")));

        UUID walkIn = saleFor(null, countingService, "PAID");
        assertFalse(loyaltyService.recordVisitForSale(walkIn));
    }

    @Test
    void aBusinessWithoutTheLoyaltyModuleOrWithTheProgramOffEarnsNothing() {
        UUID sale = serviceSale(countingService, "PAID");
        loyaltyService.updateProgram(new LoyaltyProgramRequest(false, 10, "a free service"));
        assertFalse(loyaltyService.recordVisitForSale(sale), "program switched off");

        loyaltyService.updateProgram(new LoyaltyProgramRequest(true, 10, "a free service"));
        jdbc.update("UPDATE businesses SET enabled_modules = ARRAY['INVENTORY','SALES','CUSTOMERS','EXPENSES'] WHERE id = ?", businessId);
        assertFalse(loyaltyService.recordVisitForSale(sale), "module not enabled");
    }

    @Test
    void aCompletedServiceOrderCountsAndSharesTheDayWithASale() {
        UUID order = serviceOrder(countingService, ServiceOrderStatus.COMPLETED);
        assertTrue(loyaltyService.recordVisitForServiceOrder(order));

        // A sale the same day is the same visit, not a second one.
        assertFalse(loyaltyService.recordVisitForSale(serviceSale(countingService, "PAID")));
        assertEquals(1, loyaltyService.statusFor(businessId, customerId).visits());
    }

    @Test
    void aServiceOrderThatIsNotCompletedYetDoesNotCount() {
        assertFalse(loyaltyService.recordVisitForServiceOrder(serviceOrder(countingService, ServiceOrderStatus.RECEIVED)));
    }

    @Test
    void reachingTheTargetMakesARewardDueAndRedeemingConsumesExactlyThatMany() {
        loyaltyService.updateProgram(new LoyaltyProgramRequest(true, 3, "a free wash"));
        earnVisits(4);

        LoyaltyStatusResponse due = loyaltyService.statusFor(businessId, customerId);
        assertEquals(4, due.visits());
        assertTrue(due.rewardDue());
        assertEquals(1, due.rewardsDue());

        LoyaltyStatusResponse after = loyaltyService.redeem(customerId);

        assertEquals(1, after.visits(), "4 earned - 3 consumed = 1 toward the next");
        assertFalse(after.rewardDue());
        assertEquals(1, count("SELECT count(*) FROM loyalty_redemptions WHERE business_id = ?"));
    }

    @Test
    void aRewardCannotBeRedeemedBeforeItIsEarned() {
        earnVisits(2);

        assertThrows(ApiException.class, () -> loyaltyService.redeem(customerId));
        assertEquals(2, loyaltyService.statusFor(businessId, customerId).visits());
        assertEquals(0, count("SELECT count(*) FROM loyalty_redemptions WHERE business_id = ?"));
    }

    @Test
    void onlyOneOfManySimultaneousRedeemClicksGetsTheSingleReward() throws Exception {
        loyaltyService.updateProgram(new LoyaltyProgramRequest(true, 3, "a free wash"));
        earnVisits(3);

        int clicks = 8; // a cashier and a manager both pressing it, double-clicks, two tills...
        ExecutorService pool = Executors.newFixedThreadPool(clicks);
        CountDownLatch ready = new CountDownLatch(clicks);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < clicks; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                TenantContext.setBusinessId(businessId);
                try {
                    loyaltyService.redeem(customerId);
                    return true;
                } catch (ApiException notDue) {
                    return false;
                } finally {
                    TenantContext.clear();
                }
            }));
        }
        ready.await();
        go.countDown();
        int succeeded = 0;
        for (Future<Boolean> f : results) if (f.get()) succeeded++;
        pool.shutdown();

        assertEquals(1, succeeded, "one reward earned, one reward given");
        assertEquals(0, loyaltyService.statusFor(businessId, customerId).visits());
        assertEquals(1, count("SELECT count(*) FROM loyalty_redemptions WHERE business_id = ?"));
    }

    @Test
    void changingTheTargetLaterDoesNotRewriteHistory() {
        loyaltyService.updateProgram(new LoyaltyProgramRequest(true, 3, "a free wash"));
        earnVisits(3);
        loyaltyService.redeem(customerId); // consumed 3

        loyaltyService.updateProgram(new LoyaltyProgramRequest(true, 10, "a free service"));

        LoyaltyStatusResponse status = loyaltyService.statusFor(businessId, customerId);
        assertEquals(0, status.visits(), "the redeemed visits stay consumed under the new target");
        assertEquals(10, status.target());
    }

    @Test
    void aRewardOfAnotherBusinessesCustomerCannotBeRedeemedHere() {
        loyaltyService.updateProgram(new LoyaltyProgramRequest(true, 1, "x"));
        earnVisits(1);
        TenantContext.setBusinessId(UUID.randomUUID());

        assertThrows(ApiException.class, () -> loyaltyService.redeem(customerId));
    }

    @Test
    void theCustomersOwnPageShowsOnlyTheMinimumAndRejectsBadLinks() {
        loyaltyService.updateProgram(new LoyaltyProgramRequest(true, 10, "a free service"));
        earnVisits(1);
        String token = loyaltyService.statusFor(businessId, customerId).pointsUrl().replaceFirst(".*/p/", "");

        PublicLoyaltyResponse page = loyaltyService.publicPage(token);

        assertEquals("Shell Luxury Hair", page.businessName());
        assertEquals("Dennis", page.customerFirstName(), "first name only");
        assertEquals(1, page.visits());
        assertEquals(10, page.target());
        assertTrue(page.bookingPath().startsWith("/book/shell-"));

        assertThrows(ApiException.class, () -> loyaltyService.publicPage("nope-nope-"));
        assertThrows(ApiException.class, () -> loyaltyService.publicPage(null));

        jdbc.update("UPDATE businesses SET enabled_modules = ARRAY['INVENTORY'] WHERE id = ?", businessId);
        assertThrows(ApiException.class, () -> loyaltyService.publicPage(token), "link stops working if the program is withdrawn");
    }

    @Test
    void theReceiptLineSaysPointsOrRewardDue() {
        loyaltyService.updateProgram(new LoyaltyProgramRequest(true, 2, "a free wash"));
        assertNull(loyaltyService.receiptLine(businessId, customerId), "nothing to say before a first visit");

        earnVisits(1);
        assertEquals("Points 1/2", loyaltyService.receiptLine(businessId, customerId));

        earnVisits(1);
        assertEquals("Reward due!", loyaltyService.receiptLine(businessId, customerId));
    }

    // ---- helpers ----

    // Earns n visits on n different days by back-dating the ones already counted.
    private void earnVisits(int n) {
        for (int i = 0; i < n; i++) {
            UUID sale = serviceSale(countingService, "PAID");
            jdbc.update("UPDATE loyalty_visits SET visit_date = visit_date - 1 - ? WHERE business_id = ? AND customer_id = ?",
                    i, businessId, customerId);
            loyaltyService.recordVisitForSale(sale);
        }
    }

    private UUID serviceSale(UUID catalogId, String paymentStatus) {
        return saleFor(customerId, catalogId, paymentStatus);
    }

    private UUID saleFor(UUID customer, UUID catalogId, String paymentStatus) {
        return sale(customer, paymentStatus, SaleItemType.SERVICE, catalogId);
    }

    private UUID sale(String paymentStatus, SaleItemType type, UUID catalogId) {
        return sale(customerId, paymentStatus, type, catalogId);
    }

    private UUID sale(UUID customer, String paymentStatus, SaleItemType type, UUID catalogId) {
        Sale sale = saleRepository.save(Sale.builder()
                .businessId(businessId).customerId(customer).paymentMethod(PaymentMethod.CASH)
                .paymentStatus(paymentStatus).totalAmount(new BigDecimal("100")).amountPaid(new BigDecimal("100")).build());
        saleItemRepository.save(SaleItem.builder()
                .businessId(businessId).saleId(sale.getId()).itemType(type).serviceCatalogId(catalogId)
                .productName("Item").unitPrice(new BigDecimal("100")).quantity(1).subtotal(new BigDecimal("100")).build());
        return sale.getId();
    }

    private UUID serviceOrder(UUID catalogId, ServiceOrderStatus status) {
        return serviceOrderRepository.save(ServiceOrder.builder()
                .businessId(businessId).serviceTypeId(serviceTypeId).customerId(customerId)
                .serviceCatalogId(catalogId).status(status).price(new BigDecimal("100")).build()).getId();
    }

    private int count(String sql) {
        Number n = jdbc.queryForObject(sql, Number.class, businessId);
        return n == null ? 0 : n.intValue();
    }
}
