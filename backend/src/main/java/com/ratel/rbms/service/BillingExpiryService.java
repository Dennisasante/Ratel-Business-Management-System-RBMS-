package com.ratel.rbms.service;

import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.BillingStatus;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Daily sweep: flips businesses whose trial or paid period has lapsed into
 * READ_ONLY, and warns owners a few days ahead of time so it's never a total
 * surprise. Mirrors DigestService's shape — same "loop every business, mail
 * its Owners" structure, just for a different kind of daily fact.
 */
@Service
public class BillingExpiryService {

    private static final int REMINDER_WINDOW_DAYS = 3;
    private static final int GRACE_PERIOD_DAYS = 7;

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;

    public BillingExpiryService(
            BusinessRepository businessRepository,
            UserRepository userRepository,
            EmailService emailService,
            ActivityLogService activityLogService
    ) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public void runDailyCheck() {
        Instant now = Instant.now();

        flipExpiredTrials(now);
        flipExpiredSubscriptionsToGrace(now);
        flipExpiredGraceToReadOnly(now);

        Instant reminderWindowEnd = now.plus(REMINDER_WINDOW_DAYS, ChronoUnit.DAYS);
        sendTrialReminders(now, reminderWindowEnd);
        sendSubscriptionReminders(now, reminderWindowEnd);
        sendGraceReminders();
    }

    private void flipExpiredTrials(Instant now) {
        List<Business> expired = businessRepository.findAllByBillingStatusAndTrialEndsAtBefore(BillingStatus.TRIALING, now);
        for (Business business : expired) {
            business.setBillingStatus(BillingStatus.READ_ONLY);
            businessRepository.save(business);
            activityLogService.log(business.getId(), null,
                    "Free trial ended — account moved to read-only until a plan is chosen", "BUSINESS", business.getId());
        }
    }

    // A lapsed paid subscription gets a 7-day grace window (still fully
    // usable — see ReadOnlyEnforcementFilter, which only blocks READ_ONLY)
    // before flipExpiredGraceToReadOnly() below catches it on a later run.
    private void flipExpiredSubscriptionsToGrace(Instant now) {
        List<Business> expired = businessRepository.findAllByBillingStatusAndCurrentPeriodEndsAtBefore(BillingStatus.ACTIVE, now);
        for (Business business : expired) {
            business.setBillingStatus(BillingStatus.GRACE);
            business.setGracePeriodEndsAt(now.plus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS));
            // Reset so sendGraceReminders() below sends a fresh notice for this
            // grace period, same reset-on-transition pattern the subscription
            // reminder itself relies on.
            business.setExpiryReminderSentAt(null);
            businessRepository.save(business);
            activityLogService.log(business.getId(), null,
                    "Subscription period ended — " + GRACE_PERIOD_DAYS + "-day grace period started", "BUSINESS", business.getId());
        }
    }

    private void flipExpiredGraceToReadOnly(Instant now) {
        List<Business> expired = businessRepository.findAllByBillingStatusAndGracePeriodEndsAtBefore(BillingStatus.GRACE, now);
        for (Business business : expired) {
            business.setBillingStatus(BillingStatus.READ_ONLY);
            businessRepository.save(business);
            activityLogService.log(business.getId(), null,
                    "Grace period ended — account moved to read-only until renewed", "BUSINESS", business.getId());
        }
    }

    private void sendTrialReminders(Instant now, Instant windowEnd) {
        List<Business> dueSoon = businessRepository.findAllByBillingStatusAndTrialEndsAtBetweenAndExpiryReminderSentAtIsNull(
                BillingStatus.TRIALING, now, windowEnd);
        for (Business business : dueSoon) {
            sendReminder(business, business.getTrialEndsAt(), "free trial");
        }
    }

    private void sendSubscriptionReminders(Instant now, Instant windowEnd) {
        List<Business> dueSoon = businessRepository.findAllByBillingStatusAndCurrentPeriodEndsAtBetweenAndExpiryReminderSentAtIsNull(
                BillingStatus.ACTIVE, now, windowEnd);
        for (Business business : dueSoon) {
            sendReminder(business, business.getCurrentPeriodEndsAt(), "subscription");
        }
    }

    // Unlike the trial/subscription reminders (a window before a future
    // deadline), this fires once for every business currently sitting in
    // GRACE with no notice sent yet — there's no "coming soon" window here,
    // the grace period has already started.
    private void sendGraceReminders() {
        List<Business> inGrace = businessRepository.findAllByBillingStatusAndExpiryReminderSentAtIsNull(BillingStatus.GRACE);
        for (Business business : inGrace) {
            List<User> owners = userRepository.findAllByBusinessIdAndRole(business.getId(), Role.OWNER).stream()
                    .filter(User::isActive)
                    .toList();
            if (owners.isEmpty()) {
                continue;
            }

            long graceDaysRemaining = business.getGracePeriodEndsAt() != null
                    ? Math.max(0, ChronoUnit.DAYS.between(Instant.now(), business.getGracePeriodEndsAt()))
                    : 0;
            for (User owner : owners) {
                emailService.sendGracePeriodNotice(owner.getEmail(), business.getName(), graceDaysRemaining);
            }

            business.setExpiryReminderSentAt(Instant.now());
            businessRepository.save(business);
        }
    }

    private void sendReminder(Business business, Instant deadline, String periodLabel) {
        List<User> owners = userRepository.findAllByBusinessIdAndRole(business.getId(), Role.OWNER).stream()
                .filter(User::isActive)
                .toList();
        if (owners.isEmpty()) {
            return;
        }

        long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(Instant.now(), deadline));
        for (User owner : owners) {
            emailService.sendBillingReminder(owner.getEmail(), business.getName(), daysRemaining, periodLabel);
        }

        // One reminder per deadline, regardless of how many Owners got it —
        // reset back to null whenever BillingService extends the period, so
        // the next billing cycle gets its own reminder.
        business.setExpiryReminderSentAt(Instant.now());
        businessRepository.save(business);
    }
}
