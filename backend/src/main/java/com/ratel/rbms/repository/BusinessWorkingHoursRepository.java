package com.ratel.rbms.repository;

import com.ratel.rbms.entity.BusinessWorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessWorkingHoursRepository extends JpaRepository<BusinessWorkingHours, UUID> {

    List<BusinessWorkingHours> findAllByBusinessIdOrderByDayOfWeek(UUID businessId);

    Optional<BusinessWorkingHours> findByBusinessIdAndDayOfWeek(UUID businessId, int dayOfWeek);

    void deleteAllByBusinessId(UUID businessId);
}
