package com.ratel.rbms.config;

import com.ratel.rbms.service.BillingExpiryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BillingExpiryScheduler {

    private final BillingExpiryService billingExpiryService;

    public BillingExpiryScheduler(BillingExpiryService billingExpiryService) {
        this.billingExpiryService = billingExpiryService;
    }

    @Scheduled(cron = "${app.billing.expiry-cron}")
    public void run() {
        billingExpiryService.runDailyCheck();
    }
}
