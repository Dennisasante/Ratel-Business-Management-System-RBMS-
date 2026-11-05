package com.ratel.rbms.repository;

import com.ratel.rbms.entity.SmsMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SmsMessageRepository extends JpaRepository<SmsMessage, UUID> {

    List<SmsMessage> findTop50ByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    Optional<SmsMessage> findFirstBySourceIdAndKindOrderByCreatedAtDesc(UUID sourceId, String kind);
}
