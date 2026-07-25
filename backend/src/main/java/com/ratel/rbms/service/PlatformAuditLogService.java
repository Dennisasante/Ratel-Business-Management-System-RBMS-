package com.ratel.rbms.service;

import com.ratel.rbms.dto.PlatformAuditLogResponse;
import com.ratel.rbms.entity.PlatformAuditLog;
import com.ratel.rbms.repository.PlatformAdminRepository;
import com.ratel.rbms.repository.PlatformAuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PlatformAuditLogService {

    private final PlatformAuditLogRepository repository;
    private final PlatformAdminRepository platformAdminRepository;

    public PlatformAuditLogService(PlatformAuditLogRepository repository, PlatformAdminRepository platformAdminRepository) {
        this.repository = repository;
        this.platformAdminRepository = platformAdminRepository;
    }

    public void log(UUID adminId, String action, UUID businessId, String businessNameSnapshot, UUID targetUserId) {
        PlatformAuditLog entry = PlatformAuditLog.builder()
                .adminId(adminId)
                .action(action)
                .businessId(businessId)
                .businessNameSnapshot(businessNameSnapshot)
                .targetUserId(targetUserId)
                .build();
        repository.save(entry);
    }

    public List<PlatformAuditLogResponse> listAll() {
        return repository.findTop300ByOrderByCreatedAtDesc().stream()
                .map(log -> new PlatformAuditLogResponse(
                        log.getId(),
                        log.getAction(),
                        log.getBusinessId(),
                        log.getBusinessNameSnapshot(),
                        log.getAdminId() != null
                                ? platformAdminRepository.findById(log.getAdminId()).map(a -> a.getFullName()).orElse("Unknown")
                                : "Unknown",
                        log.getCreatedAt()
                ))
                .toList();
    }
}
