package com.ratel.rbms.config;

import com.ratel.rbms.service.WeeklyDigestService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WeeklyDigestScheduler {

    private final WeeklyDigestService weeklyDigestService;

    public WeeklyDigestScheduler(WeeklyDigestService weeklyDigestService) {
        this.weeklyDigestService = weeklyDigestService;
    }

    @Scheduled(cron = "${app.weekly-digest.cron}")
    public void run() {
        weeklyDigestService.sendOwnerWeeklyDigests();
        weeklyDigestService.sendSuperAdminWeeklyDigest();
    }
}
