package com.ratel.rbms.service;

import com.ratel.rbms.dto.HelpRequestRequest;
import com.ratel.rbms.dto.HelpRequestResponse;
import com.ratel.rbms.entity.HelpRequest;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.HelpRequestRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HelpRequestService {

    private final HelpRequestRepository helpRequestRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    public HelpRequestService(
            HelpRequestRepository helpRequestRepository,
            UserRepository userRepository,
            ActivityLogService activityLogService
    ) {
        this.helpRequestRepository = helpRequestRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
    }

    public List<HelpRequestResponse> listAll() {
        return helpRequestRepository.findAllByBusinessIdOrderByCreatedAtDesc(TenantContext.getBusinessId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public HelpRequestResponse create(HelpRequestRequest req) {
        User requester = userRepository.findById(TenantContext.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));

        HelpRequest helpRequest = HelpRequest.builder()
                .businessId(TenantContext.getBusinessId())
                .userId(requester.getId())
                .requesterName(requester.getFullName())
                .requesterEmail(requester.getEmail())
                .category(req.category())
                .subject(req.subject())
                .message(req.message())
                .build();
        helpRequest = helpRequestRepository.save(helpRequest);
        activityLogService.log(
                "Sent a support request: " + helpRequest.getSubject(),
                "HELP_REQUEST", helpRequest.getId()
        );
        return toResponse(helpRequest);
    }

    private HelpRequestResponse toResponse(HelpRequest r) {
        return new HelpRequestResponse(
                r.getId(), r.getRequesterName(), r.getRequesterEmail(), r.getCategory(),
                r.getSubject(), r.getMessage(), r.getStatus(), r.getAdminResponse(),
                r.getRespondedAt(), r.getCreatedAt()
        );
    }
}
