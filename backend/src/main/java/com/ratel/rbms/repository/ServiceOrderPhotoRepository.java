package com.ratel.rbms.repository;

import com.ratel.rbms.entity.ServiceOrderPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderPhotoRepository extends JpaRepository<ServiceOrderPhoto, UUID> {

    List<ServiceOrderPhoto> findAllByServiceOrderIdAndBusinessIdOrderByCreatedAtDesc(UUID serviceOrderId, UUID businessId);

    Optional<ServiceOrderPhoto> findByIdAndBusinessId(UUID id, UUID businessId);
}
