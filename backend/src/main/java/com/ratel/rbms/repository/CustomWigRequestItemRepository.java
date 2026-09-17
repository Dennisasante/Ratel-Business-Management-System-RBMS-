package com.ratel.rbms.repository;

import com.ratel.rbms.entity.CustomWigRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomWigRequestItemRepository extends JpaRepository<CustomWigRequestItem, UUID> {

    List<CustomWigRequestItem> findAllByRequestIdOrderByDisplayOrderAsc(UUID requestId);
}
