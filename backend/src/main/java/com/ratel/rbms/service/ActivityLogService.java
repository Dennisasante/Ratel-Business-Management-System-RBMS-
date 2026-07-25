package com.ratel.rbms.service;

import com.ratel.rbms.dto.ActivityLogResponse;
import com.ratel.rbms.entity.ActivityLog;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.repository.ActivityLogRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;

    public ActivityLogService(
            ActivityLogRepository activityLogRepository,
            BusinessRepository businessRepository,
            UserRepository userRepository
    ) {
        this.activityLogRepository = activityLogRepository;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
    }

    /**
     * Low-level write. Used directly for pre-auth events (login, registration)
     * where TenantContext isn't populated yet, since those happen before the
     * request has a JWT to derive it from.
     */
    public void log(UUID businessId, UUID userId, String action, String entityType, UUID entityId) {
        ActivityLog entry = ActivityLog.builder()
                .businessId(businessId)
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .build();
        activityLogRepository.save(entry);
    }

    /**
     * Convenience wrapper for anything happening inside an authenticated
     * request — pulls business/user straight from TenantContext instead of
     * making every call site pass them through.
     */
    public void log(String action, String entityType, UUID entityId) {
        log(TenantContext.getBusinessId(), TenantContext.getUserId(), action, entityType, entityId);
    }

    public List<ActivityLogResponse> listForBusiness(UUID businessId) {
        String businessName = businessRepository.findById(businessId).map(Business::getName).orElse("Unknown");
        return toResponses(activityLogRepository.findTop300ByBusinessIdOrderByCreatedAtDesc(businessId), Map.of(businessId, businessName));
    }

    /** Filtered version backing the Activity Log page's staff/date controls. Every param is optional. */
    public List<ActivityLogResponse> searchForBusiness(UUID businessId, UUID userId, Instant from, Instant to) {
        String businessName = businessRepository.findById(businessId).map(Business::getName).orElse("Unknown");
        List<ActivityLog> logs = activityLogRepository.search(businessId, userId, from, to, PageRequest.of(0, 300));
        return toResponses(logs, Map.of(businessId, businessName));
    }

    /** Platform-wide, across every business — Super Admin only. */
    public List<ActivityLogResponse> listAll() {
        List<ActivityLog> logs = activityLogRepository.findTop300ByOrderByCreatedAtDesc();
        Map<UUID, String> businessNames = new HashMap<>();
        for (Business b : businessRepository.findAll()) {
            businessNames.put(b.getId(), b.getName());
        }
        return toResponses(logs, businessNames);
    }

    /** Filtered platform-wide search — businessId is also optional here (null = every business). */
    public List<ActivityLogResponse> searchAll(UUID businessId, UUID userId, Instant from, Instant to) {
        List<ActivityLog> logs = activityLogRepository.search(businessId, userId, from, to, PageRequest.of(0, 300));
        Map<UUID, String> businessNames = new HashMap<>();
        for (Business b : businessRepository.findAll()) {
            businessNames.put(b.getId(), b.getName());
        }
        return toResponses(logs, businessNames);
    }

    private List<ActivityLogResponse> toResponses(List<ActivityLog> logs, Map<UUID, String> businessNames) {
        return logs.stream()
                .map(log -> ActivityLogResponse.from(
                        log,
                        businessNames.getOrDefault(log.getBusinessId(), "Unknown"),
                        log.getUserId() != null
                                ? userRepository.findById(log.getUserId()).map(User::getFullName).orElse("Unknown")
                                : "System"
                ))
                .toList();
    }
}
