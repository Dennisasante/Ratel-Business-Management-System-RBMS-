package com.ratel.rbms.service;

import com.ratel.rbms.dto.CustomItemAttributeOptionRequest;
import com.ratel.rbms.dto.CustomItemAttributeOptionResponse;
import com.ratel.rbms.dto.CustomItemAttributeRequest;
import com.ratel.rbms.dto.CustomItemAttributeResponse;
import com.ratel.rbms.entity.CustomItemAttribute;
import com.ratel.rbms.entity.CustomItemAttributeOption;
import com.ratel.rbms.entity.enums.AttributeSelectionType;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.CustomItemAttributeOptionRepository;
import com.ratel.rbms.repository.CustomItemAttributeRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Owner-configured pricing rules for the custom wig configurator — mirrors
 * ServiceTypeService's shape: a simple named list, each item owned by the
 * current tenant. An attribute's options are edited as a whole set (see
 * CustomItemAttributeRequest), not individually CRUD'd.
 */
@Service
public class CustomItemAttributeService {

    private final CustomItemAttributeRepository attributeRepository;
    private final CustomItemAttributeOptionRepository optionRepository;

    public CustomItemAttributeService(
            CustomItemAttributeRepository attributeRepository,
            CustomItemAttributeOptionRepository optionRepository
    ) {
        this.attributeRepository = attributeRepository;
        this.optionRepository = optionRepository;
    }

    public List<CustomItemAttributeResponse> list() {
        UUID businessId = TenantContext.getBusinessId();
        return attributeRepository.findAllByBusinessIdOrderBySortOrderAsc(businessId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CustomItemAttributeResponse create(CustomItemAttributeRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        CustomItemAttribute attribute = CustomItemAttribute.builder()
                .businessId(businessId)
                .name(req.name())
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .selectionType(parseSelectionType(req.selectionType()))
                .stepGroup(blankToNull(req.stepGroup()))
                .build();
        attribute = attributeRepository.save(attribute);
        saveOptions(attribute.getId(), req.options());
        return toResponse(attribute);
    }

    @Transactional
    public CustomItemAttributeResponse update(UUID id, CustomItemAttributeRequest req) {
        CustomItemAttribute attribute = getOwned(id);
        attribute.setName(req.name());
        attribute.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        attribute.setSelectionType(parseSelectionType(req.selectionType()));
        attribute.setStepGroup(blankToNull(req.stepGroup()));
        attribute = attributeRepository.save(attribute);

        optionRepository.deleteAllByAttributeId(attribute.getId());
        saveOptions(attribute.getId(), req.options());

        return toResponse(attribute);
    }

    private AttributeSelectionType parseSelectionType(String value) {
        if (value == null || value.isBlank()) return AttributeSelectionType.SINGLE;
        try {
            return AttributeSelectionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Selection type must be SINGLE or MULTIPLE.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public void delete(UUID id) {
        CustomItemAttribute attribute = getOwned(id);
        // Options cascade-delete at the DB level (ON DELETE CASCADE).
        attributeRepository.delete(attribute);
    }

    private void saveOptions(UUID attributeId, List<CustomItemAttributeOptionRequest> options) {
        int i = 0;
        for (CustomItemAttributeOptionRequest opt : options) {
            optionRepository.save(CustomItemAttributeOption.builder()
                    .attributeId(attributeId)
                    .label(opt.label())
                    .priceModifier(opt.priceModifier() != null ? opt.priceModifier() : BigDecimal.ZERO)
                    .sortOrder(opt.sortOrder() != null ? opt.sortOrder() : i)
                    .requiresManualQuote(opt.requiresManualQuote() != null && opt.requiresManualQuote())
                    .build());
            i++;
        }
    }

    private CustomItemAttribute getOwned(UUID id) {
        return attributeRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attribute not found."));
    }

    private CustomItemAttributeResponse toResponse(CustomItemAttribute attribute) {
        List<CustomItemAttributeOptionResponse> options = optionRepository.findAllByAttributeIdOrderBySortOrderAsc(attribute.getId()).stream()
                .map(CustomItemAttributeOptionResponse::from)
                .toList();
        return CustomItemAttributeResponse.from(attribute, options);
    }
}
