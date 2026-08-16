package com.ratel.rbms.service;

import com.ratel.rbms.dto.NotificationResponse;
import com.ratel.rbms.entity.Notification;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.NotificationRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// Write side is deliberately dumb, same philosophy as PaymentTransactionService.record()
// — just inserts, called from the same transaction as the booking/request creation
// it's about, so it can't desync from what actually happened.
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void create(UUID businessId, String type, String title, String body, String sourceType, UUID sourceId) {
        notificationRepository.save(Notification.builder()
                .businessId(businessId)
                .type(type)
                .title(title)
                .body(body)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .build());
    }

    public List<NotificationResponse> listAll() {
        return notificationRepository.findTop50ByBusinessIdOrderByCreatedAtDesc(TenantContext.getBusinessId()).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public long unreadCount() {
        return notificationRepository.countByBusinessIdAndReadFalse(TenantContext.getBusinessId());
    }

    @Transactional
    public void markRead(UUID id) {
        UUID businessId = TenantContext.getBusinessId();
        Notification notification = notificationRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found."));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead() {
        notificationRepository.markAllRead(TenantContext.getBusinessId());
    }
}
