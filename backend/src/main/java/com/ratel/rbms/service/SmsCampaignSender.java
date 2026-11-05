package com.ratel.rbms.service;

import com.ratel.rbms.entity.BusinessSmsAccount;
import com.ratel.rbms.entity.SmsCampaign;
import com.ratel.rbms.entity.SmsCampaignRecipient;
import com.ratel.rbms.repository.SmsAccountRepository;
import com.ratel.rbms.repository.SmsCampaignRecipientRepository;
import com.ratel.rbms.repository.SmsCampaignRepository;
import com.ratel.rbms.sms.SmsGateway;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sends the queued texts of running campaigns, in the background. The queue lives in the database, so a
 * restart simply carries on where it left off, and a text is claimed with SKIP LOCKED so it can never be
 * sent twice. The credits were already taken when the campaign was created; here a failed text just marks
 * the row, and the campaign's settlement hands the credit back.
 */
@Component
public class SmsCampaignSender {

    private static final Logger log = LoggerFactory.getLogger(SmsCampaignSender.class);

    static final int BATCH_SIZE = 25;
    static final int PARALLEL_SENDS = 4;
    private static final Duration STUCK_AFTER = Duration.ofMinutes(15);

    private final SmsCampaignRecipientRepository recipientRepository;
    private final SmsCampaignRepository campaignRepository;
    private final SmsAccountRepository accountRepository;
    private final SmsGateway gateway;
    private final SmsCampaignService campaignService;
    private final TransactionTemplate tx;
    // Off only in tests, which drive the sender by hand so what happens when is deterministic.
    private final boolean kickEnabled;
    private final ReentrantLock drainLock = new ReentrantLock();
    private final ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_SENDS);

    public SmsCampaignSender(
            SmsCampaignRecipientRepository recipientRepository,
            SmsCampaignRepository campaignRepository,
            SmsAccountRepository accountRepository,
            SmsGateway gateway,
            @Lazy SmsCampaignService campaignService,
            PlatformTransactionManager transactionManager,
            @Value("${app.sms.campaign-kick-enabled:true}") boolean kickEnabled
    ) {
        this.recipientRepository = recipientRepository;
        this.campaignRepository = campaignRepository;
        this.accountRepository = accountRepository;
        this.gateway = gateway;
        this.campaignService = campaignService;
        this.tx = new TransactionTemplate(transactionManager);
        this.kickEnabled = kickEnabled;
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }

    /** Called right after a campaign is created, so sending starts at once instead of at the next tick. */
    @Async
    public void kick() {
        if (kickEnabled) {
            drain();
        }
    }

    /** The safety net: also picks up anything left over (e.g. after a restart). */
    @Scheduled(fixedDelayString = "${app.sms.campaign-poll-ms:3000}")
    public void tick() {
        drain();
    }

    /** Sends everything that's waiting. Only one drain runs at a time in this process. */
    public void drain() {
        if (!drainLock.tryLock()) {
            return;
        }
        try {
            recoverStuck();
            // With no provider key, leave everything queued rather than fail it all.
            if (!gateway.isConfigured()) {
                return;
            }
            while (processBatch() > 0) {
                // keep going until nothing is left
            }
        } catch (RuntimeException e) {
            log.error("Campaign sender hit an unexpected problem ({})", e.getClass().getSimpleName());
        } finally {
            drainLock.unlock();
        }
    }

    // Texts claimed long ago that never reported back count as unsent (their credit goes back).
    private void recoverStuck() {
        Integer stuck = tx.execute(status -> recipientRepository.failStuck(Instant.now().minus(STUCK_AFTER)));
        if (stuck != null && stuck > 0) {
            for (SmsCampaign c : campaignRepository.findAllByStatus(SmsCampaign.SENDING)) {
                campaignService.settle(c.getId());
            }
        }
    }

    /** Claims, sends and records up to one batch. Returns how many it handled (0 = nothing waiting). */
    int processBatch() {
        List<SmsCampaignRecipient> claimed = tx.execute(status -> {
            List<UUID> ids = recipientRepository.lockPendingIds(BATCH_SIZE);
            if (ids.isEmpty()) {
                return List.<SmsCampaignRecipient>of();
            }
            recipientRepository.markSending(ids, Instant.now());
            return recipientRepository.findAllById(ids);
        });
        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }

        Map<UUID, String> senderByBusiness = new HashMap<>();
        for (UUID businessId : new HashSet<>(claimed.stream().map(SmsCampaignRecipient::getBusinessId).toList())) {
            BusinessSmsAccount account = accountRepository.findById(businessId).orElse(null);
            senderByBusiness.put(businessId, account == null ? null : account.getSenderId());
        }

        List<Future<?>> inFlight = new ArrayList<>();
        for (SmsCampaignRecipient r : claimed) {
            inFlight.add(pool.submit(() -> sendOne(r, senderByBusiness.get(r.getBusinessId()))));
        }
        for (Future<?> f : inFlight) {
            try {
                f.get();
            } catch (Exception e) {
                log.error("A campaign text task failed unexpectedly ({})", e.getClass().getSimpleName());
            }
        }

        Set<UUID> campaigns = new HashSet<>();
        claimed.forEach(r -> campaigns.add(r.getCampaignId()));
        campaigns.forEach(campaignService::settle);
        return claimed.size();
    }

    private void sendOne(SmsCampaignRecipient r, String senderId) {
        SmsGateway.SendResult result;
        if (senderId == null || senderId.isBlank()) {
            result = SmsGateway.SendResult.rejected("No sender name set up.");
        } else {
            try {
                result = gateway.send(senderId, r.getPhone(), r.getMessage());
            } catch (RuntimeException e) {
                log.error("SMS gateway threw during a campaign ({})", e.getClass().getSimpleName());
                result = SmsGateway.SendResult.rejected("Unexpected provider error.");
            }
        }
        SmsGateway.SendResult finalResult = result;
        tx.executeWithoutResult(status -> {
            if (finalResult.accepted()) {
                recipientRepository.markSent(r.getId(), truncate(finalResult.providerRef(), 100), Instant.now());
            } else {
                recipientRepository.markFailed(r.getId(), truncate(finalResult.error(), 300));
            }
        });
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
