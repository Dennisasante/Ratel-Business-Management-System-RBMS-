package com.ratel.rbms.service;

import com.ratel.rbms.dto.StaffMemberRequest;
import com.ratel.rbms.dto.StaffMemberResponse;
import com.ratel.rbms.entity.StaffMember;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.StaffMemberRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// Deliberately thin compared to UserManagementService — no password, email,
// role, or login to manage. Just a name (and optional phone/notes) to assign
// work to. See StaffMember for why this replaced STAFF-role User accounts.
@Service
public class StaffMemberService {

    private final StaffMemberRepository staffMemberRepository;
    private final ActivityLogService activityLogService;

    public StaffMemberService(StaffMemberRepository staffMemberRepository, ActivityLogService activityLogService) {
        this.staffMemberRepository = staffMemberRepository;
        this.activityLogService = activityLogService;
    }

    public List<StaffMemberResponse> listAll() {
        UUID businessId = TenantContext.getBusinessId();
        return staffMemberRepository.findAllByBusinessIdOrderByFullNameAsc(businessId).stream()
                .map(StaffMemberResponse::from)
                .toList();
    }

    public StaffMemberResponse create(StaffMemberRequest req) {
        StaffMember staffMember = StaffMember.builder()
                .businessId(TenantContext.getBusinessId())
                .fullName(req.fullName())
                .phone(req.phone())
                .notes(req.notes())
                .build();
        staffMember = staffMemberRepository.save(staffMember);

        activityLogService.log("Added staff member \"" + staffMember.getFullName() + "\"", "STAFF_MEMBER", staffMember.getId());

        return StaffMemberResponse.from(staffMember);
    }

    public StaffMemberResponse update(UUID id, StaffMemberRequest req) {
        StaffMember staffMember = getOwned(id);
        staffMember.setFullName(req.fullName());
        staffMember.setPhone(req.phone());
        staffMember.setNotes(req.notes());
        staffMember = staffMemberRepository.save(staffMember);

        activityLogService.log("Updated staff member \"" + staffMember.getFullName() + "\"", "STAFF_MEMBER", staffMember.getId());

        return StaffMemberResponse.from(staffMember);
    }

    public StaffMemberResponse setActive(UUID id, boolean active) {
        StaffMember staffMember = getOwned(id);
        staffMember.setActive(active);
        staffMember = staffMemberRepository.save(staffMember);

        activityLogService.log(
                (active ? "Reactivated" : "Deactivated") + " staff member \"" + staffMember.getFullName() + "\"",
                "STAFF_MEMBER", staffMember.getId()
        );

        return StaffMemberResponse.from(staffMember);
    }

    private StaffMember getOwned(UUID id) {
        return staffMemberRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Staff member not found."));
    }
}
