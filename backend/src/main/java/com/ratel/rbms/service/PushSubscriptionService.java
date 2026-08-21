package com.ratel.rbms.service;

import com.ratel.rbms.dto.PushSubscribeRequest;
import com.ratel.rbms.entity.PushSubscription;
import com.ratel.rbms.repository.PushSubscriptionRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository pushSubscriptionRepository;

    public PushSubscriptionService(PushSubscriptionRepository pushSubscriptionRepository) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    // endpoint is unique per browser installation — re-subscribing the same
    // one (e.g. after clearing site data, or switching which user is logged
    // in on a shared device) upserts rather than creating a duplicate row.
    public void subscribe(PushSubscribeRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        UUID userId = TenantContext.getUserId();
        PushSubscription sub = pushSubscriptionRepository.findByEndpoint(req.endpoint())
                .orElseGet(() -> PushSubscription.builder().endpoint(req.endpoint()).build());
        sub.setBusinessId(businessId);
        sub.setUserId(userId);
        sub.setP256dh(req.p256dh());
        sub.setAuth(req.auth());
        pushSubscriptionRepository.save(sub);
    }

    @Transactional
    public void unsubscribe(String endpoint) {
        pushSubscriptionRepository.deleteByEndpoint(endpoint);
    }
}
