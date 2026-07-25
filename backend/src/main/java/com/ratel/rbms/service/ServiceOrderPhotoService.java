package com.ratel.rbms.service;

import com.ratel.rbms.dto.ServiceOrderPhotoResponse;
import com.ratel.rbms.entity.ServiceOrderPhoto;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.ServiceOrderPhotoRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Same upload-to-disk approach as BusinessService's logo upload — one photo per
// file, many photos per order (before/after shots, a client's reference image, etc.).
@Service
public class ServiceOrderPhotoService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final ServiceOrderPhotoRepository photoRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final ActivityLogService activityLogService;
    private final String uploadDir;

    public ServiceOrderPhotoService(
            ServiceOrderPhotoRepository photoRepository,
            ServiceOrderRepository serviceOrderRepository,
            ActivityLogService activityLogService,
            @Value("${app.upload-dir}") String uploadDir
    ) {
        this.photoRepository = photoRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.activityLogService = activityLogService;
        this.uploadDir = uploadDir;
    }

    public List<ServiceOrderPhotoResponse> list(UUID serviceOrderId) {
        UUID businessId = TenantContext.getBusinessId();
        assertOrderOwned(serviceOrderId, businessId);
        return photoRepository.findAllByServiceOrderIdAndBusinessIdOrderByCreatedAtDesc(serviceOrderId, businessId).stream()
                .map(ServiceOrderPhotoResponse::from)
                .toList();
    }

    public ServiceOrderPhotoResponse upload(UUID serviceOrderId, MultipartFile file) {
        UUID businessId = TenantContext.getBusinessId();
        assertOrderOwned(serviceOrderId, businessId);

        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No file was uploaded.");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Photo must be a PNG, JPEG, or WEBP image.");
        }

        String extension = switch (file.getContentType()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String filename = UUID.randomUUID() + extension;

        try {
            Path photosDir = Paths.get(uploadDir, "service-order-photos", serviceOrderId.toString());
            Files.createDirectories(photosDir);
            Path target = photosDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Couldn't save the photo. Please try again.");
        }

        ServiceOrderPhoto photo = ServiceOrderPhoto.builder()
                .businessId(businessId)
                .serviceOrderId(serviceOrderId)
                .url("/uploads/service-order-photos/" + serviceOrderId + "/" + filename)
                .build();
        photo = photoRepository.save(photo);

        activityLogService.log("Added a photo to service order", "SERVICE_ORDER", serviceOrderId);

        return ServiceOrderPhotoResponse.from(photo);
    }

    public void delete(UUID serviceOrderId, UUID photoId) {
        UUID businessId = TenantContext.getBusinessId();
        assertOrderOwned(serviceOrderId, businessId);

        ServiceOrderPhoto photo = photoRepository.findByIdAndBusinessId(photoId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Photo not found."));
        if (!photo.getServiceOrderId().equals(serviceOrderId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Photo not found.");
        }

        photoRepository.delete(photo);

        // Best-effort cleanup — the DB record is the source of truth either way,
        // so a failed file delete here shouldn't fail the request.
        try {
            String relativePath = photo.getUrl().replaceFirst("^/uploads/", "");
            Files.deleteIfExists(Paths.get(uploadDir, relativePath));
        } catch (IOException ignored) {
        }

        activityLogService.log("Removed a photo from service order", "SERVICE_ORDER", serviceOrderId);
    }

    private void assertOrderOwned(UUID serviceOrderId, UUID businessId) {
        serviceOrderRepository.findByIdAndBusinessId(serviceOrderId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service order not found."));
    }
}
