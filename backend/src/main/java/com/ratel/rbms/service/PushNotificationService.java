package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratel.rbms.entity.PushSubscription;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.repository.PushSubscriptionRepository;
import com.ratel.rbms.repository.UserRepository;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// The single fan-out point for browser push, called from
// NotificationService.create() right after the in-app row is saved — every
// current and future in-app notification type gets push "for free" with no
// other call-site changes needed. @Async so a slow/dead push endpoint can
// never affect the caller's own transaction.
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushSubscriptionService pushSubscriptionService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    // Null when VAPID keys aren't configured — same "log and no-op" posture
    // as EmailService when SMTP isn't set, so local dev/test never needs them.
    private final PushService pushService;

    public PushNotificationService(
            PushSubscriptionRepository pushSubscriptionRepository,
            PushSubscriptionService pushSubscriptionService,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            @Value("${app.vapid.public-key}") String vapidPublicKey,
            @Value("${app.vapid.private-key}") String vapidPrivateKey,
            @Value("${app.vapid.subject}") String vapidSubject
    ) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.pushSubscriptionService = pushSubscriptionService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;

        if (vapidPublicKey == null || vapidPublicKey.isBlank() || vapidPrivateKey == null || vapidPrivateKey.isBlank()) {
            this.pushService = null;
            System.out.println("[RBMS] Push notifications not configured (VAPID_PUBLIC_KEY/VAPID_PRIVATE_KEY blank) — skipping.");
            return;
        }

        Security.addProvider(new BouncyCastleProvider());
        try {
            this.pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid VAPID keys configured for push notifications.", e);
        }
    }

    @Async
    public void notifyBusinessOwnersAndManagers(UUID businessId, String title, String body) {
        if (pushService == null) {
            return;
        }

        List<UUID> recipientIds = userRepository.findAllByBusinessIdAndRoleIn(businessId, List.of(Role.OWNER, Role.MANAGER)).stream()
                .filter(User::isActive)
                .map(User::getId)
                .toList();
        if (recipientIds.isEmpty()) {
            return;
        }

        List<PushSubscription> subscriptions = pushSubscriptionRepository.findAllByBusinessIdAndUserIdIn(businessId, recipientIds);
        if (subscriptions.isEmpty()) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of("title", title, "body", body));
        } catch (Exception e) {
            log.warn("Couldn't serialize push payload for business {}", businessId, e);
            return;
        }

        for (PushSubscription subscription : subscriptions) {
            sendOne(subscription, payload);
        }
    }

    private void sendOne(PushSubscription subscription, String payload) {
        try {
            nl.martijndwars.webpush.Notification notification = nl.martijndwars.webpush.Notification.builder()
                    .endpoint(subscription.getEndpoint())
                    .userPublicKey(subscription.getP256dh())
                    .userAuth(subscription.getAuth())
                    .payload(payload)
                    .build();
            HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();
            // 404/410 means the browser unsubscribed or the endpoint expired
            // — the subscription is permanently dead, so stop tracking it
            // instead of retrying forever. Delegates to PushSubscriptionService
            // (a different bean) rather than calling the repository directly —
            // deleteByEndpoint is a derived delete query and needs an active
            // transaction, which self-invoking a @Transactional method on
            // `this` would silently skip (Spring's proxy never sees the call).
            if (status == 404 || status == 410) {
                pushSubscriptionService.unsubscribe(subscription.getEndpoint());
            }
        } catch (Exception e) {
            log.warn("Push send failed for subscription {}", subscription.getId(), e);
        }
    }
}
