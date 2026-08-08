package com.ratel.rbms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratel.rbms.dto.CustomWigRequestCreatedResponse;
import com.ratel.rbms.dto.PublicCustomWigConfigResponse;
import com.ratel.rbms.dto.SubmitCustomWigRequestRequest;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.service.CustomWigRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * No JWT — a pasted-in-WordPress widget calls this directly, same reasoning
 * as PublicBookingController. The submission endpoint accepts multipart
 * (a JSON "payload" part plus an optional "photo" file part) in one request,
 * rather than create-then-attach-photo as two separate public writes.
 */
@RestController
@RequestMapping("/api/public/custom-wig")
public class PublicCustomWigController {

    private final CustomWigRequestService customWigRequestService;
    private final ObjectMapper objectMapper;

    public PublicCustomWigController(CustomWigRequestService customWigRequestService, ObjectMapper objectMapper) {
        this.customWigRequestService = customWigRequestService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/config")
    public PublicCustomWigConfigResponse config(@RequestParam UUID businessId) {
        return customWigRequestService.getConfig(businessId);
    }

    @PostMapping(value = "/requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CustomWigRequestCreatedResponse> submit(
            @RequestParam UUID businessId,
            @RequestParam("payload") String payloadJson,
            @RequestParam(value = "photo", required = false) MultipartFile photo
    ) {
        SubmitCustomWigRequestRequest payload;
        try {
            payload = objectMapper.readValue(payloadJson, SubmitCustomWigRequestRequest.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Malformed request.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(customWigRequestService.submit(businessId, payload, photo));
    }
}
