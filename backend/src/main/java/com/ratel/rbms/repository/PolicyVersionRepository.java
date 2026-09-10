package com.ratel.rbms.repository;

import com.ratel.rbms.entity.PolicyVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, UUID> {

    // "Current version" is derived — the highest versionNumber for a policy — never a stored
    // pointer. See PolicyVersion's own class comment for why.
    Optional<PolicyVersion> findTopByPolicyIdOrderByVersionNumberDesc(UUID policyId);
}
