package com.ratel.rbms.controller;

import com.ratel.rbms.dto.HelpRequestRequest;
import com.ratel.rbms.dto.HelpRequestResponse;
import com.ratel.rbms.service.HelpRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// No @PreAuthorize role restriction — every staff role (including STAFF) can
// ask the platform for help, unlike most business-management endpoints.
@RestController
@RequestMapping("/api/help-requests")
public class HelpRequestController {

    private final HelpRequestService helpRequestService;

    public HelpRequestController(HelpRequestService helpRequestService) {
        this.helpRequestService = helpRequestService;
    }

    @GetMapping
    public List<HelpRequestResponse> list() {
        return helpRequestService.listAll();
    }

    @PostMapping
    public ResponseEntity<HelpRequestResponse> create(@Valid @RequestBody HelpRequestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(helpRequestService.create(request));
    }
}
