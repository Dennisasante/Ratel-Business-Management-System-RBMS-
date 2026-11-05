package com.ratel.rbms.service;

import com.ratel.rbms.entity.BusinessSmsAccount;
import com.ratel.rbms.entity.SmsCreditLedger;
import com.ratel.rbms.entity.SmsMessage;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.SmsAccountRepository;
import com.ratel.rbms.repository.SmsCreditLedgerRepository;
import com.ratel.rbms.repository.SmsMessageRepository;
import com.ratel.rbms.sms.SmsGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The money rules of SmsService.send(): reserve first, never call out unpaid, refund on failure. */
class SmsServiceTest {

    private final UUID businessId = UUID.randomUUID();

    private SmsGateway gateway;
    private SmsAccountRepository accounts;
    private SmsMessageRepository messages;
    private SmsCreditLedgerRepository ledger;
    private SmsService service;

    @BeforeEach
    void setUp() {
        gateway = mock(SmsGateway.class);
        accounts = mock(SmsAccountRepository.class);
        messages = mock(SmsMessageRepository.class);
        ledger = mock(SmsCreditLedgerRepository.class);

        when(gateway.isConfigured()).thenReturn(true);
        BusinessSmsAccount account = BusinessSmsAccount.builder()
                .businessId(businessId).senderId("GLOWSALON").senderStatus(BusinessSmsAccount.SENDER_APPROVED)
                .lowBalanceThreshold(20).build();
        when(accounts.findById(businessId)).thenReturn(Optional.of(account));
        when(messages.save(any(SmsMessage.class))).thenAnswer(inv -> {
            SmsMessage m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            when(messages.findById(m.getId())).thenReturn(Optional.of(m));
            return m;
        });

        service = new SmsService(gateway, accounts, messages, ledger, mock(ModuleAccessService.class),
                mock(NotificationService.class), mock(PlatformTransactionManager.class));
    }

    @Test
    void notEnoughCreditsStopsBeforeTheProviderIsCalled() {
        when(accounts.debit(businessId, 1)).thenReturn(null);

        assertThrows(ApiException.class, () -> service.send(businessId, "0244123456", "Hi", SmsMessage.KIND_TEST, null));

        verify(gateway, never()).send(any(), any(), any());
        verify(messages, never()).save(any());
    }

    @Test
    void anUnapprovedSenderNameIsRefusedWithoutSpendingCredits() {
        BusinessSmsAccount pending = BusinessSmsAccount.builder()
                .businessId(businessId).senderId("GLOWSALON").senderStatus(BusinessSmsAccount.SENDER_PENDING).build();
        when(accounts.findById(businessId)).thenReturn(Optional.of(pending));

        assertThrows(ApiException.class, () -> service.send(businessId, "0244123456", "Hi", SmsMessage.KIND_TEST, null));

        verify(accounts, never()).debit(any(), anyInt());
        verify(gateway, never()).send(any(), any(), any());
    }

    @Test
    void anInvalidNumberIsRefusedWithoutSpendingCredits() {
        assertThrows(ApiException.class, () -> service.send(businessId, "12345", "Hi", SmsMessage.KIND_TEST, null));

        verify(accounts, never()).debit(any(), anyInt());
    }

    @Test
    void whenTheProviderIsNotConfiguredNothingIsCharged() {
        when(gateway.isConfigured()).thenReturn(false);

        assertThrows(ApiException.class, () -> service.send(businessId, "0244123456", "Hi", SmsMessage.KIND_TEST, null));

        verify(accounts, never()).debit(any(), anyInt());
    }

    @Test
    void aSuccessfulSendIsMarkedSentAndKeepsTheCharge() {
        when(accounts.debit(businessId, 1)).thenReturn(49);
        when(gateway.send("GLOWSALON", "0244123456", "Hi")).thenReturn(SmsGateway.SendResult.ok("ref-1"));

        SmsMessage result = service.send(businessId, "0244 123 456", "Hi", SmsMessage.KIND_TEST, null);

        assertEquals(SmsMessage.SENT, result.getStatus());
        assertEquals("ref-1", result.getProviderRef());
        assertEquals(1, result.getCreditsUsed());
        verify(accounts, never()).credit(any(), anyInt());
    }

    @Test
    void aProviderRejectionRefundsTheCreditsAndMarksTheMessageFailed() {
        when(accounts.debit(businessId, 1)).thenReturn(49);
        when(accounts.credit(businessId, 1)).thenReturn(50);
        when(gateway.send(any(), any(), any())).thenReturn(SmsGateway.SendResult.rejected("[2002] Insufficient balance"));

        SmsMessage result = service.send(businessId, "0244123456", "Hi", SmsMessage.KIND_TEST, null);

        assertEquals(SmsMessage.FAILED, result.getStatus());
        verify(accounts).credit(businessId, 1);
        ArgumentCaptor<SmsCreditLedger> entries = ArgumentCaptor.forClass(SmsCreditLedger.class);
        verify(ledger, times(2)).save(entries.capture());
        assertEquals(-1, entries.getAllValues().get(0).getDelta());
        assertEquals(1, entries.getAllValues().get(1).getDelta());
    }

    @Test
    void aGatewayCrashIsTreatedAsAFailureAndRefunded() {
        when(accounts.debit(businessId, 1)).thenReturn(49);
        when(accounts.credit(businessId, 1)).thenReturn(50);
        when(gateway.send(any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        SmsMessage result = service.send(businessId, "0244123456", "Hi", SmsMessage.KIND_TEST, null);

        assertEquals(SmsMessage.FAILED, result.getStatus());
        verify(accounts).credit(businessId, 1);
    }

    @Test
    void aLongerMessageChargesOneCreditPerSegment() {
        when(accounts.debit(businessId, 2)).thenReturn(48);
        when(gateway.send(any(), any(), any())).thenReturn(SmsGateway.SendResult.ok("ref-2"));

        SmsMessage result = service.send(businessId, "0244123456", "a".repeat(200), SmsMessage.KIND_TEST, null);

        assertEquals(2, result.getCreditsUsed());
        verify(accounts).debit(businessId, 2);
    }
}
