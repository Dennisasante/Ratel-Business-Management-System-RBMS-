package com.ratel.rbms.config;

import com.ratel.rbms.service.DigestService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DigestScheduler {

    private final DigestService digestService;

    public DigestScheduler(DigestService digestService) {
        this.digestService = digestService;
    }

    @Scheduled(cron = "${app.digest.cron}")
    public void run() {
        digestService.sendDailyDigests();
    }
}
