package com.ratel.rbms.repository;

import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.enums.AiChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiChannelBindingRepository extends JpaRepository<AiChannelBinding, UUID> {

    // The one lookup AiChannelRouter's external path resolves identity
    // through — never trust a caller-supplied business_id, only ever this.
    Optional<AiChannelBinding> findByChannelAndExternalAccountId(AiChannel channel, String externalAccountId);

    List<AiChannelBinding> findAllByBusinessId(UUID businessId);

    Optional<AiChannelBinding> findByIdAndBusinessId(UUID id, UUID businessId);
}
