package com.ratel.rbms.service;

import com.ratel.rbms.dto.SmsPurchaseVerifyResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessSmsAccount;
import com.ratel.rbms.entity.SmsCreditPurchase;
import com.ratel.rbms.entity.SmsMessage;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.SmsAccountRepository;
import com.ratel.rbms.repository.SmsCreditLedgerRepository;
import com.ratel.rbms.repository.SmsCreditPurchaseRepository;
import com.ratel.rbms.sms.SmsGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Real PostgreSQL, real separate connections, deliberately NOT @Transactional at
 * the test level: the properties under test (no overspending, credit exactly
 * once, real rollback) only mean something when each call commits for real.
 * Only the two outside services (the SMS provider and Paystack) are faked.
 */
@SpringBootTest
class SmsCreditsIntegrationTest {

    @Autowired private SmsService smsService;
    @Autowired private SmsCreditService smsCreditService;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private SmsAccountRepository accountRepository;
    @Autowired private SmsCreditPurchaseRepository purchaseRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @SpyBean private SmsCreditLedgerRepository ledgerRepository;
    @MockBean private SmsGateway gateway;
    @MockBean private PaystackService paystackService;

    private UUID businessId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Business business = businessRepository.save(Business.builder()
                .name("SMS Credits Test " + unique)
                .slug("sms-credits-test-" + unique)
                .industry(Industry.OTHER)
                .currency("GHS")
                .enabledModules(List.of("INVENTORY", "SALES", "CUSTOMERS", "EXPENSES", "SMS"))
                .build());
        businessId = business.getId();

        smsService.ensureAccount(businessId);
        jdbc.update("UPDATE business_sms_accounts SET sender_id = 'TESTCO', sender_status = 'APPROVED' WHERE business_id = ?", businessId);

        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.send(anyString(), anyString(), anyString())).thenReturn(SmsGateway.SendResult.ok("provider-ref"));
        when(paystackService.resolvePlatformSecretKey()).thenReturn("sk_test_fake");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM sms_messages WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM sms_credit_ledger WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM sms_credit_purchases WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM business_sms_accounts WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM notifications WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM businesses WHERE id = ?", businessId);
    }

    @Test
    void manyConcurrentSendsNeverSpendMoreThanTheBalance() throws Exception {
        grant(5);

        int attempts = 25;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            String phone = String.format("0244%06d", i);
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    smsService.send(businessId, phone, "Hello", SmsMessage.KIND_TEST, null);
                    return true;
                } catch (ApiException notEnoughCredits) {
                    return false;
                }
            }));
        }
        ready.await();
        go.countDown();
        int succeeded = 0;
        for (Future<Boolean> f : results) if (f.get()) succeeded++;
        pool.shutdown();

        assertEquals(5, succeeded, "exactly as many sends as credits");
        assertEquals(0, smsService.balanceOf(businessId));
        assertEquals(5, count("SELECT count(*) FROM sms_messages WHERE business_id = ? AND status = 'SENT'"));
        assertEquals(5, count("SELECT count(*) FROM sms_credit_ledger WHERE business_id = ? AND type = 'SEND'"));
        // The ledger agrees with the balance: every movement adds up.
        assertEquals(-5, count("SELECT coalesce(sum(delta), 0) FROM sms_credit_ledger WHERE business_id = ?"));
    }

    @Test
    void aProviderFailureRefundsTheCreditsAndLeavesAMatchingLedger() {
        grant(10);
        when(gateway.send(anyString(), anyString(), anyString())).thenReturn(SmsGateway.SendResult.rejected("[2002] Insufficient balance"));

        SmsMessage result = smsService.send(businessId, "0244123456", "Hello", SmsMessage.KIND_TEST, null);

        assertEquals(SmsMessage.FAILED, result.getStatus());
        assertEquals(10, smsService.balanceOf(businessId), "a failed send costs nothing");
        assertEquals(1, count("SELECT count(*) FROM sms_credit_ledger WHERE business_id = ? AND type = 'SEND'"));
        assertEquals(1, count("SELECT count(*) FROM sms_credit_ledger WHERE business_id = ? AND type = 'REFUND'"));
        assertEquals(0, count("SELECT coalesce(sum(delta), 0) FROM sms_credit_ledger WHERE business_id = ?"));
    }

    @Test
    void aFailureInsideTheReservationRollsTheDebitBack() {
        grant(10);
        // Real failure partway through the first transaction, after the credits were already deducted.
        doThrow(new IllegalStateException("simulated database failure")).when(ledgerRepository).save(any());

        assertThrows(IllegalStateException.class,
                () -> smsService.send(businessId, "0244123456", "Hello", SmsMessage.KIND_TEST, null));

        reset(ledgerRepository); // back to the real repository for the assertions below
        assertEquals(10, smsService.balanceOf(businessId), "the debit must not survive a failed reservation");
        assertEquals(0, count("SELECT count(*) FROM sms_messages WHERE business_id = ?"), "nor the queued message");
    }

    @Test
    void aPaidPurchaseIsCreditedExactlyOnceEvenWhenEveryConfirmationArrivesAtOnce() throws Exception {
        String reference = newPurchase(100, "50.00");
        when(paystackService.verifyTransaction(anyString(), anyString())).thenReturn(paidResult(reference, 5000));

        int callers = 12; // webhook + browser verify + sweep, several times over
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<SmsPurchaseVerifyResponse>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return smsCreditService.verifyPurchase(reference);
            }));
        }
        ready.await();
        go.countDown();
        for (Future<SmsPurchaseVerifyResponse> f : results) assertTrue(f.get().success());
        pool.shutdown();

        assertEquals(100, smsService.balanceOf(businessId), "credited once, not twelve times");
        assertEquals(1, count("SELECT count(*) FROM sms_credit_ledger WHERE business_id = ? AND type = 'PURCHASE'"));
        assertEquals(SmsCreditPurchase.SUCCESS, purchaseRepository.findByPaystackReference(reference).orElseThrow().getStatus());
    }

    @Test
    void verifyingAnAlreadyCreditedPurchaseAgainAddsNothing() {
        String reference = newPurchase(100, "50.00");
        when(paystackService.verifyTransaction(anyString(), anyString())).thenReturn(paidResult(reference, 5000));

        smsCreditService.verifyPurchase(reference);
        SmsPurchaseVerifyResponse again = smsCreditService.verifyPurchase(reference);

        assertTrue(again.success());
        assertEquals(100, smsService.balanceOf(businessId));
    }

    @Test
    void aPaymentOfTheWrongAmountIsNotCredited() {
        String reference = newPurchase(100, "50.00");
        when(paystackService.verifyTransaction(anyString(), anyString())).thenReturn(paidResult(reference, 100)); // GHS 1.00

        SmsPurchaseVerifyResponse response = smsCreditService.verifyPurchase(reference);

        assertFalse(response.success());
        assertEquals(0, smsService.balanceOf(businessId));
        assertEquals(SmsCreditPurchase.PENDING, purchaseRepository.findByPaystackReference(reference).orElseThrow().getStatus());
    }

    @Test
    void anUnpaidPurchaseStaysPendingAndAddsNothing() {
        String reference = newPurchase(100, "50.00");
        when(paystackService.verifyTransaction(anyString(), anyString()))
                .thenReturn(new PaystackService.VerifyResult(false, "abandoned", reference, 0, "GHS", null, null, null, null));

        SmsPurchaseVerifyResponse response = smsCreditService.verifyPurchase(reference);

        assertFalse(response.success());
        assertEquals(0, smsService.balanceOf(businessId));
        assertEquals(SmsCreditPurchase.PENDING, purchaseRepository.findByPaystackReference(reference).orElseThrow().getStatus());
    }

    // ---- helpers ----

    // The repository's credit/debit statements always run inside a service transaction in production.
    private void grant(int credits) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> accountRepository.credit(businessId, credits));
    }

    private String newPurchase(int credits, String price) {
        String reference = SmsCreditService.REFERENCE_PREFIX + UUID.randomUUID().toString().substring(0, 12);
        purchaseRepository.save(SmsCreditPurchase.builder()
                .businessId(businessId)
                .bundleName("Test bundle")
                .credits(credits)
                .amount(new BigDecimal(price))
                .paystackReference(reference)
                .build());
        return reference;
    }

    private PaystackService.VerifyResult paidResult(String reference, long amountMinorUnits) {
        return new PaystackService.VerifyResult(true, "success", reference, amountMinorUnits, "GHS", "2026-11-05T10:00:00Z", null, null, null);
    }

    private int count(String sql) {
        Number n = jdbc.queryForObject(sql, Number.class, businessId);
        return n == null ? 0 : n.intValue();
    }
}
