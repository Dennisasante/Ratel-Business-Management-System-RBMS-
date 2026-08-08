package com.ratel.rbms.dto;

import com.ratel.rbms.entity.CustomItemAttribute;

import java.util.List;
import java.util.UUID;

public record CustomItemAttributeResponse(
        UUID id,
        String name,
        int sortOrder,
        List<CustomItemAttributeOptionResponse> options
) {
    public static CustomItemAttributeResponse from(CustomItemAttribute attribute, List<CustomItemAttributeOptionResponse> options) {
        return new CustomItemAttributeResponse(attribute.getId(), attribute.getName(), attribute.getSortOrder(), options);
    }
}
