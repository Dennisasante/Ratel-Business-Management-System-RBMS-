package com.ratel.rbms.config;

import com.ratel.rbms.entity.SubscriptionPayment;
import com.ratel.rbms.service.ActivityLogService;
import com.ratel.rbms.service.BillingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Self-healing backstop, added after a real production incident (2026-09-08,
 * "Chelle Luxury Hair") where a genuine payment sat unconfirmed for hours: the
 * inline client-side verify never fired (plausible for Mobile Money, which
 * confirms asynchronously after the checkout popup may already be gone), and
 * the Paystack webhook — meant to catch exactly that — was rejected with an
 * invalid-signature 401, seemingly even for what looks like a real,
 * correctly-signed Paystack delivery attempt (still unexplained; see
 * PaystackService.verifyWebhookSignature's diagnostic logging, added
 * alongside this class, for whenever it recurs). The fix landed by hand that
 * day; this makes it landing by hand a thing of the past.
 *
 * Runs far more often than the once-a-day BillingExpiryScheduler, and does
 * something fundamentally different: rather than deciding a lapsed period is
 * over, it actively re-asks Paystack whether a payment we're still waiting on
 * actually succeeded — the same check a webhook or client verify would have
 * triggered — so a stuck payment recovers within one sweep interval with no
 * manual intervention.
 *
 * Deliberately calls billingService.verifyPayment(...) here, in a different
 * class than BillingService itself, rather than having BillingService loop
 * over its own method internally — a same-class call bypasses Spring's
 * transactional proxy entirely, which would silently merge every payment in
 * a sweep into one shared transaction; in Postgres a single error there
 * poisons the whole transaction, so every payment after the failing one
 * would fail too regardless of any try/catch. Calling through the injected
 * bean here goes through the real proxy, giving each payment its own,
 * fully-isolated transaction.
 */
@Component
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final BillingService billingService;
    private final ActivityLogService activityLogService;
    private final int staleAfterMinutes;

    public PaymentReconciliationScheduler(
            BillingService billingService,
            ActivityLogService activityLogService,
            @Value("${app.billing.reconciliation-stale-after-minutes:10}") int staleAfterMinutes
    ) {
        this.billingService = billingService;
        this.activityLogService = activityLogService;
        this.staleAfterMinutes = staleAfterMinutes;
    }

    @Scheduled(cron = "${app.billing.reconciliation-cron:0 */10 * * * *}")
    public void run() {
        Instant olderThan = Instant.now().minus(staleAfterMinutes, ChronoUnit.MINUTES);
        List<SubscriptionPayment> stale = billingService.findStalePendingPayments(olderThan);

        for (SubscriptionPayment payment : stale) {
            try {
                // A genuine external call through the Spring proxy — see the
                // class-level comment on why this must not be an internal
                // self-invocation inside BillingService instead.
                billingService.verifyPayment(payment.getPaystackReference());
            } catch (Exception e) {
                // Never rethrown: a payment that's still genuinely unpaid, or
                // a transient Paystack outage, is an expected, ordinary
                // outcome here and must not stop the sweep from reaching the
                // rest of the batch. Each payment already has its own
                // transaction (see class comment), so one failure here can
                // never affect another payment's outcome.
                log.warn("Payment reconciliation sweep: verify failed for reference {} ({}) — will retry next sweep",
                        payment.getPaystackReference(), e.getMessage());
                activityLogService.log(payment.getBusinessId(), null,
                        "Automatic payment reconciliation check failed for reference " + payment.getPaystackReference()
                                + " — will retry on the next sweep",
                        "BUSINESS", payment.getBusinessId());
            }
        }
    }
}
