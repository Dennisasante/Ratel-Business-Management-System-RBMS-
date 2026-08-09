package com.ratel.rbms.controller;

import com.ratel.rbms.dto.CreateStaffRequest;
import com.ratel.rbms.dto.UpdateCommissionRateRequest;
import com.ratel.rbms.dto.UpdateUserRoleRequest;
import com.ratel.rbms.dto.UpdateUserStatusRequest;
import com.ratel.rbms.dto.UserResponse;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.service.UserManagementService;
import com.ratel.rbms.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserManagementService userManagementService;

    public UserController(UserRepository userRepository, UserManagementService userManagementService) {
        this.userRepository = userRepository;
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
    public List<UserResponse> listUsers() {
        return userRepository.findAllByBusinessId(TenantContext.getBusinessId())
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ResponseEntity<UserResponse> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.createStaff(request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return userManagementService.updateStatus(id, request);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('OWNER')")
    public UserResponse updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateUserRoleRequest request) {
        return userManagementService.updateRole(id, request);
    }

    @PatchMapping("/{id}/commission-rate")
    @PreAuthorize("hasRole('OWNER')")
    public UserResponse updateCommissionRate(@PathVariable UUID id, @Valid @RequestBody UpdateCommissionRateRequest request) {
        return userManagementService.updateCommissionRate(id, request);
    }
}
