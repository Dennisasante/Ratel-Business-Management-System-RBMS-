package com.ratel.rbms.service;

import com.ratel.rbms.dto.SmsCampaignPreviewResponse;
import com.ratel.rbms.dto.SmsCampaignRequest;
import com.ratel.rbms.dto.SmsCampaignSummaryResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.SmsAccountRepository;
import com.ratel.rbms.sms.SmsGateway;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Real PostgreSQL, real concurrent connections. A campaign spends real money across many customers, so the
 * promises are: it is paid for in full before anything goes out, nobody is texted twice, whatever isn't
 * sent is handed back exactly once, and pressing Send twice (or Cancel at the wrong moment) can't break any of that.
 */
@SpringBootTest(properties = {"app.sms.campaign-poll-ms=3600000", "app.sms.campaign-kick-enabled=false"})
class SmsCampaignIntegrationTest {

    @Autowired private SmsCampaignService campaignService;
    @Autowired private SmsService smsService;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SmsAccountRepository accountRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockBean private SmsGateway gateway;
    // Sending is driven by hand in these tests, so what happens when is deterministic.
    @Autowired private SmsCampaignSender sender;

    private UUID businessId;
    private UUID otherBusinessId;
    private UUID otherCustomerId;

    @BeforeEach
    void setUp() {
        businessId = newBusiness("Campaign Test");
        otherBusinessId = newBusiness("Other Business");
        smsService.ensureAccount(businessId);
        jdbc.update("UPDATE business_sms_accounts SET sender_id = 'TESTCO', sender_status = 'APPROVED' WHERE business_id = ?", businessId);
        grant(100);

        customer(businessId, "Ama Serwaa", "0244000001", false);
        customer(businessId, "Kofi Mensah", "0244000002", false);
        customer(businessId, "No Phone", null, false);
        customer(businessId, "Opted Out", "0244000004", true);
        customer(businessId, "Ama's Sister", "0244000001", false); // shares Ama's number
        otherCustomerId = customer(otherBusinessId, "Someone Else", "0244000099", false);

        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.send(anyString(), anyString(), anyString())).thenReturn(SmsGateway.SendResult.ok("ref"));
        TenantContext.setBusinessId(businessId);
        TenantContext.setUserId(null);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        for (UUID b : List.of(businessId, otherBusinessId)) {
            jdbc.update("DELETE FROM sms_campaign_recipients WHERE business_id = ?", b);
            jdbc.update("DELETE FROM sms_campaigns WHERE business_id = ?", b);
            jdbc.update("DELETE FROM sms_credit_ledger WHERE business_id = ?", b);
            jdbc.update("DELETE FROM business_sms_accounts WHERE business_id = ?", b);
            jdbc.update("DELETE FROM notifications WHERE business_id = ?", b);
            jdbc.update("DELETE FROM activity_logs WHERE business_id = ?", b);
            jdbc.update("DELETE FROM customers WHERE business_id = ?", b);
            jdbc.update("DELETE FROM businesses WHERE id = ?", b);
        }
    }

    // ---- Who gets it, and what it costs ----

    @Test
    void thePreviewSaysWhoGetsItWhoIsSkippedAndWhatItCosts() {
        SmsCampaignPreviewResponse p = campaignService.preview(all("Hi {name}, 20% off this week!"));

        assertEquals(2, p.recipients(), "Ama and Kofi");
        assertEquals(1, p.skippedNoPhone());
        assertEquals(1, p.skippedOptedOut());
        assertEquals(1, p.skippedDuplicate(), "Ama's sister shares her number, so one text goes there");
        assertEquals(2, p.creditsNeeded());
        assertTrue(p.enoughCredits());
        assertTrue(p.sampleMessage().startsWith("Hi Ama, 20% off this week!"), p.sampleMessage());
        assertTrue(p.sampleMessage().endsWith("Reply STOP to opt out."), "the opt-out line is on by default");
    }

    @Test
    void eachCustomerGetsTheirOwnFirstNameAndTheFooterCanBeLeftOff() {
        SmsCampaignSummaryResponse c = campaignService.create(new SmsCampaignRequest(null, "Hi {name}!", "ALL", null, false, UUID.randomUUID()));

        List<String> messages = jdbc.queryForList("SELECT message FROM sms_campaign_recipients WHERE campaign_id = ? ORDER BY phone",
                String.class, c.id());
        assertEquals(List.of("Hi Ama!", "Hi Kofi!"), messages);
    }

    @Test
    void aSelectedAudienceOnlyReachesThoseCustomersAndNeverAnotherBusinessesCustomers() {
        UUID kofi = idOf("Kofi Mensah");

        SmsCampaignPreviewResponse p = campaignService.preview(new SmsCampaignRequest(null, "Hello", "SELECTED",
                List.of(kofi, otherCustomerId), true, null));

        assertEquals(1, p.recipients(), "only Kofi: the other business's customer is ignored");
    }

    // ---- Paying for it, up front ----

    @Test
    void theWholeCostIsTakenBeforeAnythingIsSentAndTheLedgerRecordsIt() {
        SmsCampaignSummaryResponse c = campaignService.create(all("Sale!"));

        assertEquals(98, smsService.balanceOf(businessId), "2 texts reserved at once");
        assertEquals(2, c.creditsReserved());
        assertEquals("SENDING", c.status());
        assertEquals(2, count("SELECT count(*) FROM sms_campaign_recipients WHERE campaign_id = ? AND status = 'PENDING'", c.id()));
        assertEquals(-2, count("SELECT coalesce(sum(delta),0) FROM sms_credit_ledger WHERE business_id = ? AND type = 'CAMPAIGN'", null));
        verify(gateway, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void notEnoughCreditsMeansNothingStartsAndNothingIsTaken() {
        jdbc.update("UPDATE business_sms_accounts SET credit_balance = 1 WHERE business_id = ?", businessId);

        assertThrows(ApiException.class, () -> campaignService.create(all("Sale!")));

        assertEquals(1, smsService.balanceOf(businessId));
        assertEquals(0, count("SELECT count(*) FROM sms_campaigns WHERE business_id = ?", null));
        assertEquals(0, count("SELECT count(*) FROM sms_campaign_recipients WHERE business_id = ?", null));
    }

    @Test
    void twoCampaignsRacingForTheLastCreditsOnlyOneCanStart() throws Exception {
        jdbc.update("UPDATE business_sms_accounts SET credit_balance = 2 WHERE business_id = ?", businessId);

        int tries = 6;
        ExecutorService pool = Executors.newFixedThreadPool(tries);
        CountDownLatch ready = new CountDownLatch(tries);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < tries; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                TenantContext.setBusinessId(businessId);
                try {
                    campaignService.create(all("Sale!")); // each uses its own request key
                    return true;
                } catch (ApiException notEnough) {
                    return false;
                } finally {
                    TenantContext.clear();
                }
            }));
        }
        ready.await();
        go.countDown();
        int started = 0;
        for (Future<Boolean> f : results) if (f.get()) started++;
        pool.shutdown();

        assertEquals(1, started, "2 credits fund exactly one 2-text campaign");
        assertEquals(0, smsService.balanceOf(businessId));
        assertEquals(1, count("SELECT count(*) FROM sms_campaigns WHERE business_id = ?", null));
    }

    @Test
    void pressingSendTwiceOrRetryingMakesOneCampaignAndChargesOnce() throws Exception {
        UUID key = UUID.randomUUID();
        SmsCampaignRequest request = new SmsCampaignRequest(null, "Sale!", "ALL", null, true, key);

        int presses = 8;
        ExecutorService pool = Executors.newFixedThreadPool(presses);
        CountDownLatch ready = new CountDownLatch(presses);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<UUID>> results = new ArrayList<>();
        for (int i = 0; i < presses; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                TenantContext.setBusinessId(businessId);
                try {
                    return campaignService.create(request).id();
                } finally {
                    TenantContext.clear();
                }
            }));
        }
        ready.await();
        go.countDown();
        List<UUID> ids = new ArrayList<>();
        for (Future<UUID> f : results) ids.add(f.get());
        pool.shutdown();

        assertEquals(1, ids.stream().distinct().count(), "every press returned the same campaign");
        assertEquals(1, count("SELECT count(*) FROM sms_campaigns WHERE business_id = ?", null));
        assertEquals(98, smsService.balanceOf(businessId), "charged once");
    }

    // ---- Sending ----

    @Test
    void everyCustomerIsTextedAndTheCampaignFinishesWithNothingRefunded() {
        SmsCampaignSummaryResponse c = campaignService.create(all("Sale!"));

        sender.drain();

        SmsCampaignSummaryResponse done = campaignService.list().get(0);
        assertEquals(c.id(), done.id());
        assertEquals("DONE", done.status());
        assertEquals(2, done.sent());
        assertEquals(0, done.failed());
        assertEquals(0, done.waiting());
        assertEquals(98, smsService.balanceOf(businessId));
        verify(gateway, times(2)).send(eq("TESTCO"), anyString(), anyString());
        assertEquals(1, count("SELECT count(*) FROM notifications WHERE business_id = ? AND type = 'SMS_CAMPAIGN_DONE'", null));
    }

    @Test
    void aTextTheProviderRejectsIsHandedBackAndTheRestStillGoOut() {
        when(gateway.send(anyString(), eq("0244000002"), anyString())).thenReturn(SmsGateway.SendResult.rejected("[2002] nope"));
        campaignService.create(all("Sale!"));

        sender.drain();

        SmsCampaignSummaryResponse done = campaignService.list().get(0);
        assertEquals("DONE", done.status());
        assertEquals(1, done.sent());
        assertEquals(1, done.failed());
        assertEquals(1, done.creditsRefunded());
        assertEquals(99, smsService.balanceOf(businessId), "paid for 2, one failed, so 1 credit back");
        // The ledger agrees with the balance: -2 reserved, +1 returned.
        assertEquals(-1, count("SELECT coalesce(sum(delta),0) FROM sms_credit_ledger WHERE business_id = ? AND type IN ('CAMPAIGN','REFUND')", null));
    }

    @Test
    void severalSendersWorkingAtOnceNeverTextTheSameCustomerTwice() throws Exception {
        for (int i = 0; i < 60; i++) {
            customer(businessId, "Bulk " + i, String.format("0200%06d", i + 100), false);
        }
        SmsCampaignSummaryResponse c = campaignService.create(all("Sale!"));
        assertEquals(62, c.recipients());

        int workers = 5;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                while (sender.processBatch() > 0) {
                    // keep claiming batches
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        verify(gateway, times(62)).send(eq("TESTCO"), anyString(), anyString());
        assertEquals(62, count("SELECT count(*) FROM sms_campaign_recipients WHERE campaign_id = ? AND status = 'SENT'", c.id()));
        // Each number was texted exactly once.
        assertEquals(62, count("SELECT count(DISTINCT phone) FROM sms_campaign_recipients WHERE campaign_id = ?", c.id()));
    }

    @Test
    void withNoProviderKeyEverythingStaysQueuedInsteadOfFailing() {
        SmsCampaignSummaryResponse c = campaignService.create(all("Sale!"));
        when(gateway.isConfigured()).thenReturn(false);

        sender.drain();

        verify(gateway, never()).send(anyString(), anyString(), anyString());
        assertEquals(2, count("SELECT count(*) FROM sms_campaign_recipients WHERE campaign_id = ? AND status = 'PENDING'", c.id()));
        // Once the key is back, it carries on.
        when(gateway.isConfigured()).thenReturn(true);
        sender.drain();
        assertEquals(2, count("SELECT count(*) FROM sms_campaign_recipients WHERE campaign_id = ? AND status = 'SENT'", c.id()));
    }

    @Test
    void aTextStuckMidSendAfterARestartIsGivenBackNotSentTwice() {
        SmsCampaignSummaryResponse c = campaignService.create(all("Sale!"));
        jdbc.update("UPDATE sms_campaign_recipients SET status = 'SENDING', claimed_at = now() - interval '2 hours' WHERE campaign_id = ?", c.id());

        sender.drain();

        verify(gateway, never()).send(anyString(), anyString(), anyString());
        SmsCampaignSummaryResponse done = campaignService.list().get(0);
        assertEquals(2, done.failed());
        assertEquals(100, smsService.balanceOf(businessId), "both credits returned");
    }

    // ---- Stopping it ----

    @Test
    void cancellingBeforeAnythingSendsReturnsEveryCreditAndSendsNothing() {
        SmsCampaignSummaryResponse c = campaignService.create(all("Sale!"));
        assertEquals(98, smsService.balanceOf(businessId));

        SmsCampaignSummaryResponse cancelled = campaignService.cancel(c.id());

        assertEquals("CANCELLED", cancelled.status());
        assertEquals(2, cancelled.cancelled());
        assertEquals(100, smsService.balanceOf(businessId));
        sender.drain();
        verify(gateway, never()).send(anyString(), anyString(), anyString());
        assertThrows(ApiException.class, () -> campaignService.cancel(c.id()), "already stopped");
    }

    @Test
    void cancellingPartWayKeepsWhatWasSentAndReturnsTheRest() {
        for (int i = 0; i < 30; i++) {
            customer(businessId, "Bulk " + i, String.format("0200%06d", i + 100), false);
        }
        SmsCampaignSummaryResponse c = campaignService.create(all("Sale!")); // 32 texts; one batch is 25
        sender.processBatch(); // 25 go out
        assertEquals(25, count("SELECT count(*) FROM sms_campaign_recipients WHERE campaign_id = ? AND status = 'SENT'", c.id()));

        SmsCampaignSummaryResponse cancelled = campaignService.cancel(c.id());

        assertEquals(25, cancelled.sent());
        assertEquals(7, cancelled.cancelled());
        assertEquals(7, cancelled.creditsRefunded());
        assertEquals(100 - 32 + 7, smsService.balanceOf(businessId));
    }

    @Test
    void settlingManyTimesAtOnceReturnsTheCreditsExactlyOnce() throws Exception {
        when(gateway.send(anyString(), anyString(), anyString())).thenReturn(SmsGateway.SendResult.rejected("down"));
        SmsCampaignSummaryResponse c = campaignService.create(all("Sale!"));
        sender.drain(); // both fail and are returned once by the sender itself

        int settlers = 10;
        ExecutorService pool = Executors.newFixedThreadPool(settlers);
        CountDownLatch ready = new CountDownLatch(settlers);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger done = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < settlers; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                campaignService.settle(c.id());
                done.incrementAndGet();
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        assertEquals(100, smsService.balanceOf(businessId), "both credits back, once");
        assertEquals(1, count("SELECT count(*) FROM sms_credit_ledger WHERE business_id = ? AND type = 'REFUND'", null));
    }

    @Test
    void aCampaignOfAnotherBusinessIsInvisibleAndUncancellable() {
        SmsCampaignSummaryResponse c = campaignService.create(all("Sale!"));
        TenantContext.setBusinessId(otherBusinessId);

        assertThrows(ApiException.class, () -> campaignService.detail(c.id()));
        assertThrows(ApiException.class, () -> campaignService.cancel(c.id()));
        assertFalse(campaignService.list().stream().anyMatch(x -> x.id().equals(c.id())));
    }

    // ---- helpers ----

    private SmsCampaignRequest all(String message) {
        return new SmsCampaignRequest(null, message, "ALL", null, true, UUID.randomUUID());
    }

    private UUID newBusiness(String name) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return businessRepository.save(Business.builder()
                .name(name + " " + unique).slug("camp-" + unique).industry(Industry.OTHER).currency("GHS")
                .enabledModules(List.of("INVENTORY", "SALES", "CUSTOMERS", "EXPENSES", "SMS")).build()).getId();
    }

    private UUID customer(UUID business, String name, String phone, boolean optedOut) {
        return customerRepository.save(Customer.builder().businessId(business).fullName(name).phone(phone).smsOptOut(optedOut).build()).getId();
    }

    private UUID idOf(String name) {
        return customerRepository.findAllByBusinessIdOrderByFullNameAsc(businessId).stream()
                .filter(c -> c.getFullName().equals(name)).findFirst().orElseThrow().getId();
    }

    private void grant(int credits) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> accountRepository.credit(businessId, credits));
    }

    // Counts with the business id (or, when a campaign id is given, that) as the single parameter.
    private int count(String sql, UUID param) {
        Number n = jdbc.queryForObject(sql, Number.class, param == null ? businessId : param);
        return n == null ? 0 : n.intValue();
    }
}
