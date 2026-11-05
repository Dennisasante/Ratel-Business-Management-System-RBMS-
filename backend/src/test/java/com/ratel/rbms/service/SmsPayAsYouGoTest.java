package com.ratel.rbms.service;

import com.ratel.rbms.dto.CheckoutResponse;
import com.ratel.rbms.dto.SmsPlatformSettingsRequest;
import com.ratel.rbms.dto.SmsPricingResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.PlatformAdmin;
import com.ratel.rbms.entity.SmsCreditPurchase;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.PlatformAdminRepository;
import com.ratel.rbms.repository.SmsCreditPurchaseRepository;
import com.ratel.rbms.repository.UserRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Buying credits by typing an amount: credits = amount / the Super Admin's price per credit. */
@SpringBootTest
class SmsPayAsYouGoTest {

    @Autowired private SmsCreditService creditService;
    @Autowired private SmsPlatformService platformService;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private SmsCreditPurchaseRepository purchaseRepository;
    @Autowired private PlatformAdminRepository platformAdminRepository;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private SmsGateway gateway;
    @MockBean private PaystackService paystackService;
    @MockBean private UserRepository userRepository;

    private UUID businessId;
    private UUID userId;
    private UUID adminId;
    private BigDecimal originalPrice;
    private BigDecimal originalCost;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        businessId = businessRepository.save(Business.builder()
                .name("Pay As You Go " + unique).slug("payg-" + unique)
                .industry(Industry.OTHER).currency("GHS")
                .enabledModules(List.of("INVENTORY", "SALES", "CUSTOMERS", "EXPENSES", "SMS"))
                .build()).getId();
        userId = UUID.randomUUID();
        adminId = platformAdminRepository.save(PlatformAdmin.builder()
                .email("payg-admin-" + unique + "@example.com").passwordHash("x").fullName("Payg Admin").build()).getId();
        TenantContext.setBusinessId(businessId);
        TenantContext.setUserId(userId);

        when(gateway.isConfigured()).thenReturn(true);
        when(paystackService.resolvePlatformSecretKey()).thenReturn("sk_test_fake");
        when(paystackService.initializeTransaction(anyString(), anyString(), anyLong(), anyString(), anyMap()))
                .thenAnswer(inv -> new PaystackService.InitResult("access-code", inv.<String>getArgument(3), "https://paystack.test/pay"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().email("owner@payg.test").build()));

        // Remember the shared settings row so this test leaves it as it found it.
        originalPrice = jdbc.query("SELECT sell_price_per_credit FROM sms_platform_settings WHERE id = 1",
                rs -> rs.next() ? rs.getBigDecimal(1) : null);
        originalCost = jdbc.query("SELECT provider_cost_per_credit FROM sms_platform_settings WHERE id = 1",
                rs -> rs.next() ? rs.getBigDecimal(1) : null);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbc.update("DELETE FROM sms_credit_purchases WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM business_sms_accounts WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM businesses WHERE id = ?", businessId);
        jdbc.update("UPDATE sms_platform_settings SET provider_cost_per_credit = ?, sell_price_per_credit = ? WHERE id = 1", originalCost, originalPrice);
        jdbc.update("DELETE FROM platform_audit_logs WHERE admin_id = ?", adminId);
        jdbc.update("DELETE FROM platform_admins WHERE id = ?", adminId);
    }

    private void setPrice(String cost, String sell) {
        platformService.updateSettings(adminId, new SmsPlatformSettingsRequest(
                cost == null ? null : new BigDecimal(cost), sell == null ? null : new BigDecimal(sell)));
    }

    @Test
    void theCreditsAreTheAmountDividedByThePriceRoundedDown() {
        assertEquals(2000, SmsCreditService.creditsFor(new BigDecimal("100.00"), new BigDecimal("0.05")));
        assertEquals(1999, SmsCreditService.creditsFor(new BigDecimal("99.99"), new BigDecimal("0.05")), "never rounds up in the customer's favour past what was paid");
        assertEquals(2857, SmsCreditService.creditsFor(new BigDecimal("100.00"), new BigDecimal("0.035")));
    }

    @Test
    void aPurchaseRecordsExactlyTheCreditsAndAmountThePaymentIsFor() {
        setPrice("0.035", "0.05");

        CheckoutResponse checkout = creditService.startPurchaseByAmount(new BigDecimal("100"));

        SmsCreditPurchase purchase = purchaseRepository.findByPaystackReference(checkout.reference()).orElseThrow();
        assertEquals(2000, purchase.getCredits());
        assertEquals(0, new BigDecimal("100.00").compareTo(purchase.getAmount()));
        assertNull(purchase.getBundleId(), "not a fixed bundle");
        assertEquals("Top-up", purchase.getBundleName());

        ArgumentCaptor<Long> minor = ArgumentCaptor.forClass(Long.class);
        verify(paystackService).initializeTransaction(anyString(), eq("owner@payg.test"), minor.capture(), anyString(), anyMap());
        assertEquals(10000L, minor.getValue(), "Paystack is charged the typed amount in pesewas");
    }

    @Test
    void theBusinessSeesThePriceAndTheLimits() {
        setPrice(null, "0.05");

        SmsPricingResponse pricing = creditService.pricing();

        assertEquals(0, new BigDecimal("0.05").compareTo(pricing.pricePerCredit()));
        assertEquals(0, SmsCreditService.MIN_AMOUNT.compareTo(pricing.minAmount()));
        assertEquals("GHS", pricing.currency());
    }

    @Test
    void buyingIsClosedUntilASellingPriceIsSet() {
        setPrice("0.035", null);

        assertNull(creditService.pricing().pricePerCredit());
        ApiException e = assertThrows(ApiException.class, () -> creditService.startPurchaseByAmount(new BigDecimal("50")));
        assertEquals(503, e.getStatus().value());
        verify(paystackService, never()).initializeTransaction(anyString(), anyString(), anyLong(), anyString(), anyMap());
    }

    @Test
    void amountsOutsideTheLimitsAreRefusedBeforeAnyPaymentIsStarted() {
        setPrice("0.035", "0.05");

        assertThrows(ApiException.class, () -> creditService.startPurchaseByAmount(new BigDecimal("1")));
        assertThrows(ApiException.class, () -> creditService.startPurchaseByAmount(new BigDecimal("-20")));
        assertThrows(ApiException.class, () -> creditService.startPurchaseByAmount(new BigDecimal("5000.01")));
        verify(paystackService, never()).initializeTransaction(anyString(), anyString(), anyLong(), anyString(), anyMap());
    }

    @Test
    void theSettingsKeepTheCostAndThePriceIndependently() {
        setPrice("0.035", "0.05");
        assertEquals(0, new BigDecimal("0.05").compareTo(platformService.overview().sellPricePerCredit()));
        assertEquals(0, new BigDecimal("0.035").compareTo(platformService.overview().providerCostPerCredit()));
    }
}
