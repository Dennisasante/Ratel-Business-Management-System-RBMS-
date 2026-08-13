package com.ratel.rbms.repository;

import com.ratel.rbms.entity.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffMemberRepository extends JpaRepository<StaffMember, UUID> {

    List<StaffMember> findAllByBusinessIdOrderByFullNameAsc(UUID businessId);

    Optional<StaffMember> findByIdAndBusinessId(UUID id, UUID businessId);
}
