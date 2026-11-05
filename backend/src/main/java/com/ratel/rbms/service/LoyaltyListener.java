package com.ratel.rbms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Counts a visit after a sale is paid or a service order is completed — in the background,
 * after the work itself has committed, so points can never slow down or undo a sale.
 * Counting is idempotent, so it is harmless that the SMS receipt also asks for it.
 */
@Component
public class LoyaltyListener {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyListener.class);

    private final LoyaltyService loyaltyService;

    public LoyaltyListener(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSaleCompleted(SaleCompletedEvent event) {
        try {
            loyaltyService.recordVisitForSale(event.saleId());
        } catch (RuntimeException e) {
            log.error("Loyalty visit for sale {} failed ({})", event.saleId(), e.getClass().getSimpleName());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onServiceOrderCompleted(ServiceOrderCompletedEvent event) {
        try {
            loyaltyService.recordVisitForServiceOrder(event.orderId());
        } catch (RuntimeException e) {
            log.error("Loyalty visit for service order {} failed ({})", event.orderId(), e.getClass().getSimpleName());
        }
    }
}
