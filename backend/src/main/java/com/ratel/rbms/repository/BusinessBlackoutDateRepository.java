package com.ratel.rbms.repository;

import com.ratel.rbms.entity.BusinessBlackoutDate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessBlackoutDateRepository extends JpaRepository<BusinessBlackoutDate, UUID> {

    List<BusinessBlackoutDate> findAllByBusinessIdOrderByDateAsc(UUID businessId);

    Optional<BusinessBlackoutDate> findByBusinessIdAndDate(UUID businessId, LocalDate date);

    Optional<BusinessBlackoutDate> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndDate(UUID businessId, LocalDate date);
}
