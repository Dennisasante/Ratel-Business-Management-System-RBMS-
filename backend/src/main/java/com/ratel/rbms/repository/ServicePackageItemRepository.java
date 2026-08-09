package com.ratel.rbms.repository;

import com.ratel.rbms.entity.ServicePackageItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServicePackageItemRepository extends JpaRepository<ServicePackageItem, UUID> {

    List<ServicePackageItem> findAllByPackageId(UUID packageId);

    void deleteAllByPackageId(UUID packageId);
}
