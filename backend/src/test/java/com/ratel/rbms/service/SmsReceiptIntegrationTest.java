package com.ratel.rbms.service;

import com.ratel.rbms.dto.SmsReceiptStatusResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.SaleItem;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.entity.enums.PaymentMethod;
import com.ratel.rbms.entity.enums.SaleItemType;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.SaleItemRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.repository.SmsAccountRepository;
import com.ratel.rbms.sms.SmsGateway;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Real PostgreSQL. The properties that matter to a business paying for texts:
 * a sale's receipt is sent at most once however many paths ask at once, and a
 * receipt that cannot be sent is a status, never an error that could touch the sale.
 */
@SpringBootTest
class SmsReceiptIntegrationTest {

    @Autowired private SmsReceiptService receiptService;
    @Autowired private SmsService smsService;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleItemRepository saleItemRepository;
    @Autowired private SmsAccountRepository accountRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockBean private SmsGateway gateway;

    private UUID businessId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Business business = businessRepository.save(Business.builder()
                .name("Shell Luxury Hair")
                .slug("shell-" + unique)
                .industry(Industry.SALON)
                .currency("GHS")
                .enabledModules(List.of("INVENTORY", "SALES", "CUSTOMERS", "EXPENSES", "SMS", "BOOKINGS"))
                .build());
        businessId = business.getId();
        customerId = customerRepository.save(Customer.builder()
                .businessId(businessId).fullName("Dennis Asante").phone("0244112233").build()).getId();

        smsService.ensureAccount(businessId);
        jdbc.update("UPDATE business_sms_accounts SET sender_id = 'SHELLHAIR', sender_status = 'APPROVED', receipt_mode = 'SMS' WHERE business_id = ?", businessId);
        grant(10);

        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.send(anyString(), anyString(), anyString())).thenReturn(SmsGateway.SendResult.ok("provider-ref"));
        TenantContext.setBusinessId(businessId);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbc.update("DELETE FROM sms_messages WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM sms_credit_ledger WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM business_sms_accounts WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM notifications WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM loyalty_visits WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM loyalty_customers WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM loyalty_programs WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM sale_items WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM sales WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM customers WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM businesses WHERE id = ?", businessId);
    }

    @Test
    void aPaidSaleGetsOneCompactReceiptWithItsDiscount() {
        UUID saleId = paidSale(customerId, "770.00", "20.00");

        SmsReceiptStatusResponse result = receiptService.sendAutomatically(saleId, businessId);

        assertEquals(SmsReceiptService.SENT, result.status());
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(anyString(), anyString(), text.capture());
        assertTrue(text.getValue().startsWith("Thank you for choosing Shell Luxury Hair, Dennis!\n\nReceipt #"), text.getValue());
        assertTrue(text.getValue().contains("Total: GHS 770.00 (saved 20.00)"), text.getValue());
        assertTrue(text.getValue().contains("/book/shell-"), "the booking link rides along");
        assertEquals(9, smsService.balanceOf(businessId), "one credit for one compact message");
    }

    @Test
    void theSameSaleIsNeverTextedTwiceEvenWhenEveryPathAsksAtOnce() throws Exception {
        UUID saleId = paidSale(customerId, "770.00", "0");

        int callers = 10; // the event listener, the cashier's button, retries...
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<SmsReceiptStatusResponse>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            boolean manual = i % 2 == 0;
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                TenantContext.setBusinessId(businessId);
                try {
                    return manual ? receiptService.sendManually(saleId) : receiptService.sendAutomatically(saleId, businessId);
                } finally {
                    TenantContext.clear();
                }
            }));
        }
        ready.await();
        go.countDown();
        for (Future<SmsReceiptStatusResponse> f : results) {
            assertEquals(SmsReceiptService.SENT, f.get().status());
        }
        pool.shutdown();

        assertEquals(1, count("SELECT count(*) FROM sms_messages WHERE business_id = ? AND kind = 'RECEIPT'"), "one receipt row");
        assertEquals(9, smsService.balanceOf(businessId), "charged exactly once");
        verify(gateway, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void askingAgainAfterItWasSentChangesNothing() {
        UUID saleId = paidSale(customerId, "100.00", "0");
        receiptService.sendAutomatically(saleId, businessId);

        SmsReceiptStatusResponse again = receiptService.sendManually(saleId);

        assertEquals(SmsReceiptService.SENT, again.status());
        assertEquals(9, smsService.balanceOf(businessId));
        verify(gateway, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void paperOnlyBusinessesSendNothingAutomaticallyButTheCashierCanStillSendOne() {
        jdbc.update("UPDATE business_sms_accounts SET receipt_mode = 'PAPER' WHERE business_id = ?", businessId);
        UUID saleId = paidSale(customerId, "100.00", "0");

        assertEquals(SmsReceiptService.OFF, receiptService.sendAutomatically(saleId, businessId).status());
        verify(gateway, never()).send(anyString(), anyString(), anyString());
        assertTrue(receiptService.statusFor(saleId).canSend(), "the button is on offer");

        assertEquals(SmsReceiptService.SENT, receiptService.sendManually(saleId).status());
    }

    @Test
    void aWalkInSaleHasNobodyToTextAndNothingIsCharged() {
        UUID saleId = paidSale(null, "100.00", "0");

        assertEquals(SmsReceiptService.NO_CUSTOMER, receiptService.sendAutomatically(saleId, businessId).status());
        assertEquals(10, smsService.balanceOf(businessId));
    }

    @Test
    void aCustomerWithoutAPhoneIsReportedSoStaffCanAddOne() {
        UUID noPhone = customerRepository.save(Customer.builder().businessId(businessId).fullName("No Phone").build()).getId();
        UUID saleId = paidSale(noPhone, "100.00", "0");

        assertEquals(SmsReceiptService.NO_PHONE, receiptService.sendAutomatically(saleId, businessId).status());
        verify(gateway, never()).send(anyString(), anyString(), anyString());

        // Staff add the number; the same receipt can now go out.
        jdbc.update("UPDATE customers SET phone = '0201112222' WHERE id = ?", noPhone);
        assertEquals(SmsReceiptService.SENT, receiptService.sendManually(saleId).status());
    }

    @Test
    void anUnpaidSaleIsNotTextedUntilItIsPaid() {
        UUID saleId = sale(customerId, "100.00", "0", "UNPAID");

        assertEquals(SmsReceiptService.UNPAID, receiptService.sendAutomatically(saleId, businessId).status());

        jdbc.update("UPDATE sales SET payment_status = 'PAID' WHERE id = ?", saleId);
        assertEquals(SmsReceiptService.SENT, receiptService.sendAutomatically(saleId, businessId).status());
    }

    @Test
    void outOfCreditsLeavesTheSaleAloneAndReportsIt() {
        jdbc.update("UPDATE business_sms_accounts SET credit_balance = 0 WHERE business_id = ?", businessId);
        UUID saleId = paidSale(customerId, "100.00", "0");

        SmsReceiptStatusResponse result = receiptService.sendAutomatically(saleId, businessId);

        assertEquals(SmsReceiptService.NO_CREDITS, result.status());
        assertEquals(0, smsService.balanceOf(businessId));
        assertEquals("PAID", saleRepository.findById(saleId).orElseThrow().getPaymentStatus(), "the sale is untouched");
        verify(gateway, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void aProviderFailureRefundsAndCanBeRetried() {
        UUID saleId = paidSale(customerId, "100.00", "0");
        when(gateway.send(anyString(), anyString(), anyString())).thenReturn(SmsGateway.SendResult.rejected("[2002] nope"));

        SmsReceiptStatusResponse failed = receiptService.sendAutomatically(saleId, businessId);

        assertEquals(SmsReceiptService.FAILED, failed.status());
        assertEquals(10, smsService.balanceOf(businessId), "a failed receipt costs nothing");
        assertTrue(failed.canSend());

        when(gateway.send(anyString(), anyString(), anyString())).thenReturn(SmsGateway.SendResult.ok("ok-now"));
        assertEquals(SmsReceiptService.SENT, receiptService.sendManually(saleId).status());
        assertEquals(9, smsService.balanceOf(businessId));
    }

    @Test
    void aBusinessWithoutTheSmsModuleIsLeftAlone() {
        jdbc.update("UPDATE businesses SET enabled_modules = ARRAY['INVENTORY','SALES','CUSTOMERS','EXPENSES'] WHERE id = ?", businessId);
        UUID saleId = paidSale(customerId, "100.00", "0");

        assertEquals(SmsReceiptService.OFF, receiptService.sendAutomatically(saleId, businessId).status());
        verify(gateway, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void theSaleOfAnotherBusinessCannotBeTextedFromHere() {
        UUID saleId = paidSale(customerId, "100.00", "0");
        TenantContext.setBusinessId(UUID.randomUUID());

        org.junit.jupiter.api.Assertions.assertThrows(com.ratel.rbms.exception.ApiException.class,
                () -> receiptService.sendManually(saleId));
        assertFalse(count("SELECT count(*) FROM sms_messages WHERE business_id = ?") > 0);
    }

    @Test
    void withAPointsProgramTheReceiptCarriesTheCustomersPointsAndPersonalLink() {
        jdbc.update("UPDATE businesses SET enabled_modules = array_append(enabled_modules, 'LOYALTY') WHERE id = ?", businessId);
        UUID saleId = paidSale(customerId, "770.00", "20.00");

        SmsReceiptStatusResponse result = receiptService.sendAutomatically(saleId, businessId);

        assertEquals(SmsReceiptService.SENT, result.status());
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(anyString(), anyString(), text.capture());
        // This sale's visit is counted BEFORE the text is written, so it already shows 1/10.
        assertTrue(text.getValue().contains("\nYou currently have 1 point (reach 10 points to earn a reward)\n"), text.getValue());
        assertTrue(text.getValue().matches("(?s).*\\n\\nTo book an appointment or check your points:\\n\\S+/points/shell-[0-9a-f]{8}$"), text.getValue());
        assertFalse(text.getValue().contains("/book/"), "the points page link replaces the plain booking link");
        assertEquals(2, com.ratel.rbms.sms.SmsSegmentCalculator.calculate(text.getValue()).segments(), "the full wording costs two credits");
    }

    @Test
    void withoutAPointsProgramTheReceiptKeepsThePlainBookingLink() {
        UUID saleId = paidSale(customerId, "100.00", "0");

        receiptService.sendAutomatically(saleId, businessId);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(anyString(), anyString(), text.capture());
        assertFalse(text.getValue().contains("Points"), text.getValue());
        assertTrue(text.getValue().contains("To book an appointment:\n") && text.getValue().contains("/book/shell-"), text.getValue());
    }

    // ---- helpers ----

    private UUID paidSale(UUID customer, String total, String discount) {
        return sale(customer, total, discount, "PAID");
    }

    private UUID sale(UUID customer, String total, String discount, String paymentStatus) {
        Sale sale = saleRepository.save(Sale.builder()
                .businessId(businessId)
                .customerId(customer)
                .paymentMethod(PaymentMethod.CASH)
                .paymentStatus(paymentStatus)
                .totalAmount(new BigDecimal(total))
                .amountPaid("PAID".equals(paymentStatus) ? new BigDecimal(total) : BigDecimal.ZERO)
                .build());
        BigDecimal subtotal = new BigDecimal(total);
        saleItemRepository.save(SaleItem.builder()
                .businessId(businessId).saleId(sale.getId()).itemType(SaleItemType.SERVICE)
                .productName("Wig install").unitPrice(subtotal.add(new BigDecimal(discount))).quantity(1)
                .discountAmount(new BigDecimal(discount)).subtotal(subtotal).build());
        return sale.getId();
    }

    private void grant(int credits) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> accountRepository.credit(businessId, credits));
    }

    private int count(String sql) {
        Number n = jdbc.queryForObject(sql, Number.class, businessId);
        return n == null ? 0 : n.intValue();
    }
}
