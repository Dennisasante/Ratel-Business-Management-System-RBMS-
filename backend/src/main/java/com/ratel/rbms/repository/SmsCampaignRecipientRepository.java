package com.ratel.rbms.repository;

import com.ratel.rbms.entity.SmsCampaignRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SmsCampaignRecipientRepository extends JpaRepository<SmsCampaignRecipient, UUID> {

    interface StatusCount {
        String getStatus();

        long getCount();
    }

    List<SmsCampaignRecipient> findTop500ByCampaignIdOrderByPhoneAsc(UUID campaignId);

    // Picks the next texts to send and locks them; SKIP LOCKED means two senders (or two servers)
    // never pick the same one, so a customer can't be texted twice.
    @Query(value = "SELECT r.id FROM sms_campaign_recipients r JOIN sms_campaigns c ON c.id = r.campaign_id "
            + "WHERE r.status = 'PENDING' AND c.status = 'SENDING' ORDER BY r.id LIMIT :limit FOR UPDATE OF r SKIP LOCKED",
            nativeQuery = true)
    List<UUID> lockPendingIds(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE SmsCampaignRecipient r SET r.status = 'SENDING', r.claimedAt = :now WHERE r.id IN :ids")
    int markSending(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE SmsCampaignRecipient r SET r.status = 'SENT', r.providerRef = :ref, r.sentAt = :now, r.error = NULL "
            + "WHERE r.id = :id AND r.status = 'SENDING'")
    int markSent(@Param("id") UUID id, @Param("ref") String providerRef, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE SmsCampaignRecipient r SET r.status = 'FAILED', r.error = :error WHERE r.id = :id AND r.status = 'SENDING'")
    int markFailed(@Param("id") UUID id, @Param("error") String error);

    @Modifying
    @Query("UPDATE SmsCampaignRecipient r SET r.status = 'CANCELLED' WHERE r.campaignId = :campaignId AND r.status = 'PENDING'")
    int cancelPending(@Param("campaignId") UUID campaignId);

    // A text claimed long ago that never reported back (the server restarted mid-send) is treated as not
    // sent: its credit is returned. The rare possibility that it did go out is the safer way to be wrong.
    @Modifying
    @Query("UPDATE SmsCampaignRecipient r SET r.status = 'FAILED', r.error = 'No confirmation received (server restarted)' "
            + "WHERE r.status = 'SENDING' AND r.claimedAt < :cutoff")
    int failStuck(@Param("cutoff") Instant cutoff);

    @Query("SELECT r.status AS status, COUNT(r) AS count FROM SmsCampaignRecipient r WHERE r.campaignId = :campaignId GROUP BY r.status")
    List<StatusCount> countsByStatus(@Param("campaignId") UUID campaignId);

    @Query("SELECT COALESCE(SUM(r.segments), 0) FROM SmsCampaignRecipient r WHERE r.campaignId = :campaignId AND r.status IN :statuses")
    long sumSegments(@Param("campaignId") UUID campaignId, @Param("statuses") Collection<String> statuses);

    @Query("SELECT COUNT(r) FROM SmsCampaignRecipient r WHERE r.campaignId = :campaignId AND r.status IN ('PENDING', 'SENDING')")
    long countOpen(@Param("campaignId") UUID campaignId);
}
