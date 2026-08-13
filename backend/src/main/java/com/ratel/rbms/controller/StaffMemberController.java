package com.ratel.rbms.controller;

import com.ratel.rbms.dto.StaffMemberRequest;
import com.ratel.rbms.dto.StaffMemberResponse;
import com.ratel.rbms.dto.StaffMemberStatusRequest;
import com.ratel.rbms.service.StaffMemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Same read gate as GET /api/users (UserController) — anyone who can staff
// the till or the schedule can see who's available to assign work to.
// Create/update/deactivate stay open to the same set too: unlike real
// accounts, a staff-member record carries no login or permissions to
// protect, so there's no OWNER/MANAGER-only tier to enforce here.
@RestController
@RequestMapping("/api/staff-members")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class StaffMemberController {

    private final StaffMemberService staffMemberService;

    public StaffMemberController(StaffMemberService staffMemberService) {
        this.staffMemberService = staffMemberService;
    }

    @GetMapping
    public List<StaffMemberResponse> list() {
        return staffMemberService.listAll();
    }

    @PostMapping
    public ResponseEntity<StaffMemberResponse> create(@Valid @RequestBody StaffMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffMemberService.create(request));
    }

    @PutMapping("/{id}")
    public StaffMemberResponse update(@PathVariable UUID id, @Valid @RequestBody StaffMemberRequest request) {
        return staffMemberService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public StaffMemberResponse setStatus(@PathVariable UUID id, @Valid @RequestBody StaffMemberStatusRequest request) {
        return staffMemberService.setActive(id, request.active());
    }
}
