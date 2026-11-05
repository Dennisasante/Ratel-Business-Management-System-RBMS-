package com.ratel.rbms.repository;

import com.ratel.rbms.entity.SmsBundle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SmsBundleRepository extends JpaRepository<SmsBundle, UUID> {

    List<SmsBundle> findAllByActiveTrueOrderBySortOrderAscCreditsAsc();

    List<SmsBundle> findAllByOrderBySortOrderAscCreditsAsc();
}
