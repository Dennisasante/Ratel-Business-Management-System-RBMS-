package com.ratel.rbms.repository;

import com.ratel.rbms.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
    List<Business> findByNameContainingIgnoreCase(String name);
}
