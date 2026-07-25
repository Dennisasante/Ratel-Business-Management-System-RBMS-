package com.ratel.rbms.service;

import com.ratel.rbms.dto.CreateStaffRequest;
import com.ratel.rbms.dto.UpdateCommissionRateRequest;
import com.ratel.rbms.dto.UpdateUserRoleRequest;
import com.ratel.rbms.dto.UpdateUserStatusRequest;
import com.ratel.rbms.dto.UserResponse;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.AuthProvider;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Role hierarchy enforced here (not just at the controller's @PreAuthorize
 * level, which only checks "is this an Owner or Manager at all"):
 *
 *  - OWNER can create/edit/deactivate anyone, including other Owners/Managers.
 *  - MANAGER can create/edit/deactivate SALES_PERSON and ACCOUNTANT accounts
 *    only — never OWNER or MANAGER accounts (including their own role).
 *  - Nobody can deactivate or demote the business's last remaining Owner —
 *    that would permanently lock the business out of its own account.
 */
@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    public UserManagementService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ActivityLogService activityLogService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
    }

    public UserResponse createStaff(CreateStaffRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        User currentUser = currentUser();

        assertCanManage(currentUser, req.role());

        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        User staff = User.builder()
                .businessId(businessId)
                .fullName(req.fullName())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.temporaryPassword()))
                .authProvider(AuthProvider.LOCAL)
                .role(req.role())
                .active(true)
                .mustChangePassword(true)
                .build();
        staff = userRepository.save(staff);

        activityLogService.log("Added staff member \"" + staff.getFullName() + "\" as " + staff.getRole().name(), "USER", staff.getId());

        return UserResponse.from(staff);
    }

    public UserResponse updateStatus(UUID userId, UpdateUserStatusRequest req) {
        User currentUser = currentUser();
        User target = getOwned(userId);

        if (target.getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can't deactivate your own account.");
        }

        assertCanManage(currentUser, target.getRole());

        if (!req.active() && target.getRole() == Role.OWNER && countActive(target.getBusinessId(), Role.OWNER) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Can't deactivate the only Owner on the account.");
        }

        target.setActive(req.active());
        target = userRepository.save(target);

        activityLogService.log(
                (req.active() ? "Reactivated" : "Deactivated") + " staff member \"" + target.getFullName() + "\"",
                "USER", target.getId()
        );

        return UserResponse.from(target);
    }

    public UserResponse updateRole(UUID userId, UpdateUserRoleRequest req) {
        User currentUser = currentUser();
        if (currentUser.getRole() != Role.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only an Owner can change someone's role.");
        }

        User target = getOwned(userId);

        if (target.getRole() == Role.OWNER && req.role() != Role.OWNER
                && countActive(target.getBusinessId(), Role.OWNER) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Can't change the role of the only Owner on the account.");
        }

        Role oldRole = target.getRole();
        target.setRole(req.role());
        target = userRepository.save(target);

        activityLogService.log(
                "Changed \"" + target.getFullName() + "\"'s role from " + oldRole.name() + " to " + req.role().name(),
                "USER", target.getId()
        );

        return UserResponse.from(target);
    }

    public UserResponse updateCommissionRate(UUID userId, UpdateCommissionRateRequest req) {
        User currentUser = currentUser();
        if (currentUser.getRole() != Role.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only an Owner can set commission rates.");
        }

        User target = getOwned(userId);
        target.setCommissionRate(req.rate());
        target = userRepository.save(target);

        activityLogService.log(
                "Set \"" + target.getFullName() + "\"'s commission rate to " + req.rate() + "%",
                "USER", target.getId()
        );

        return UserResponse.from(target);
    }

    private User getOwned(UUID userId) {
        return userRepository.findByIdAndBusinessId(userId, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
    }

    private User currentUser() {
        return userRepository.findById(TenantContext.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));
    }

    private void assertCanManage(User currentUser, Role targetRole) {
        if (currentUser.getRole() == Role.OWNER) {
            return;
        }
        if (currentUser.getRole() == Role.MANAGER
                && targetRole != Role.OWNER && targetRole != Role.MANAGER) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You don't have permission to manage this role.");
    }

    private long countActive(UUID businessId, Role role) {
        List<User> users = userRepository.findAllByBusinessIdAndRole(businessId, role);
        return users.stream().filter(User::isActive).count();
    }
}
