package com.ratel.rbms.repository;

import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByBusinessIdAndEmail(UUID businessId, String email);

    // V1 assumes a person belongs to exactly one business, so login can resolve by email alone.
    // If/when multi-business membership per email is needed, this becomes a list + a business picker step.
    Optional<User> findByEmail(String email);

    List<User> findAllByBusinessId(UUID businessId);

    // Tenant-scoped lookup — never trust a bare findById for anything staff
    // management does, since a raw id could belong to another business.
    Optional<User> findByIdAndBusinessId(UUID id, UUID businessId);

    // Used by the "don't deactivate/demote the last Owner" guard rails.
    List<User> findAllByBusinessIdAndRole(UUID businessId, Role role);

    // Used by the Super Admin's business list to show who owns each account.
    Optional<User> findFirstByBusinessIdAndRole(UUID businessId, Role role);

    // Per-business usage count (super admin stats) — avoids loading the full
    // list just to call .size() when only a count is needed.
    long countByBusinessId(UUID businessId);
}
