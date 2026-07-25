package com.ratel.rbms.dto;

import com.ratel.rbms.entity.ServiceOrderPhoto;

import java.time.Instant;
import java.util.UUID;

public record ServiceOrderPhotoResponse(
        UUID id,
        String url,
        Instant createdAt
) {
    public static ServiceOrderPhotoResponse from(ServiceOrderPhoto photo) {
        return new ServiceOrderPhotoResponse(photo.getId(), photo.getUrl(), photo.getCreatedAt());
    }
}
