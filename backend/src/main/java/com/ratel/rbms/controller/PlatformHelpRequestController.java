package com.ratel.rbms.controller;

import com.ratel.rbms.dto.PlatformHelpRequestResponse;
import com.ratel.rbms.dto.RespondHelpRequestRequest;
import com.ratel.rbms.service.PlatformHelpRequestService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform/help-requests")
public class PlatformHelpRequestController {

    private final PlatformHelpRequestService platformHelpRequestService;

    public PlatformHelpRequestController(PlatformHelpRequestService platformHelpRequestService) {
        this.platformHelpRequestService = platformHelpRequestService;
    }

    @GetMapping
    public List<PlatformHelpRequestResponse> list() {
        return platformHelpRequestService.listAll();
    }

    @PatchMapping("/{id}/respond")
    public PlatformHelpRequestResponse respond(@PathVariable UUID id, @Valid @RequestBody RespondHelpRequestRequest request) {
        return platformHelpRequestService.respond(currentAdminId(), id, request);
    }

    private UUID currentAdminId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
