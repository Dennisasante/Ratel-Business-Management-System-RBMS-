package com.ratel.rbms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the SMS receipt after a sale is safely committed, on a background thread:
 * the cashier's "complete order" never waits on the SMS provider, and a provider
 * problem can never undo or fail the sale. fallbackExecution covers the paths
 * that complete a sale outside a surrounding transaction.
 */
@Component
public class SmsReceiptListener {

    private static final Logger log = LoggerFactory.getLogger(SmsReceiptListener.class);

    private final SmsReceiptService smsReceiptService;

    public SmsReceiptListener(SmsReceiptService smsReceiptService) {
        this.smsReceiptService = smsReceiptService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSaleCompleted(SaleCompletedEvent event) {
        try {
            smsReceiptService.sendAutomatically(event.saleId(), event.businessId());
        } catch (RuntimeException e) {
            // Never propagates: the sale is already done and this is best-effort.
            log.error("SMS receipt for sale {} failed unexpectedly ({})", event.saleId(), e.getClass().getSimpleName());
        }
    }
}
