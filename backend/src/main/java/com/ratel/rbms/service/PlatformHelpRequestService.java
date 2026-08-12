package com.ratel.rbms.service;

import com.ratel.rbms.dto.PlatformHelpRequestResponse;
import com.ratel.rbms.dto.RespondHelpRequestRequest;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.HelpRequest;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.HelpRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlatformHelpRequestService {

    private final HelpRequestRepository helpRequestRepository;
    private final BusinessRepository businessRepository;
    private final PlatformAuditLogService platformAuditLogService;

    public PlatformHelpRequestService(
            HelpRequestRepository helpRequestRepository,
            BusinessRepository businessRepository,
            PlatformAuditLogService platformAuditLogService
    ) {
        this.helpRequestRepository = helpRequestRepository;
        this.businessRepository = businessRepository;
        this.platformAuditLogService = platformAuditLogService;
    }

    public List<PlatformHelpRequestResponse> listAll() {
        List<HelpRequest> requests = helpRequestRepository.findAllByOrderByCreatedAtDesc();
        Map<UUID, String> businessNames = new HashMap<>();
        for (Business b : businessRepository.findAll()) {
            businessNames.put(b.getId(), b.getName());
        }
        return requests.stream()
                .map(r -> toResponse(r, businessNames.getOrDefault(r.getBusinessId(), "Unknown")))
                .toList();
    }

    public PlatformHelpRequestResponse respond(UUID adminId, UUID id, RespondHelpRequestRequest req) {
        HelpRequest helpRequest = helpRequestRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found."));

        helpRequest.setAdminResponse(req.response());
        helpRequest.setStatus("RESOLVED");
        helpRequest.setRespondedBy(adminId);
        helpRequest.setRespondedAt(Instant.now());
        helpRequest = helpRequestRepository.save(helpRequest);

        String businessName = businessRepository.findById(helpRequest.getBusinessId())
                .map(Business::getName).orElse("Unknown");
        platformAuditLogService.log(
                adminId, "Responded to support request: " + helpRequest.getSubject(),
                helpRequest.getBusinessId(), businessName, null
        );
        return toResponse(helpRequest, businessName);
    }

    private PlatformHelpRequestResponse toResponse(HelpRequest r, String businessName) {
        return new PlatformHelpRequestResponse(
                r.getId(), r.getBusinessId(), businessName, r.getRequesterName(), r.getRequesterEmail(),
                r.getCategory(), r.getSubject(), r.getMessage(), r.getStatus(), r.getAdminResponse(),
                r.getRespondedAt(), r.getCreatedAt()
        );
    }
}
