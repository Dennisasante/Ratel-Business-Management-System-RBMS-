package com.ratel.rbms.service;

import com.ratel.rbms.dto.BusinessResponse;
import com.ratel.rbms.dto.BusinessUpdateRequest;
import com.ratel.rbms.dto.UpdateSlugRequest;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
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
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class BusinessService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final BusinessRepository businessRepository;
    private final ActivityLogService activityLogService;
    private final String uploadDir;

    public BusinessService(
            BusinessRepository businessRepository,
            ActivityLogService activityLogService,
            @Value("${app.upload-dir}") String uploadDir
    ) {
        this.businessRepository = businessRepository;
        this.activityLogService = activityLogService;
        this.uploadDir = uploadDir;
    }

    public BusinessResponse getMine() {
        return BusinessResponse.from(getOwned());
    }

    public BusinessResponse updateProfile(BusinessUpdateRequest req) {
        Business business = getOwned();
        business.setName(req.name());
        business.setIndustry(req.industry());
        business.setLocation(req.location());
        business.setContactEmail(req.contactEmail());
        business.setContactPhone(req.contactPhone());
        business = businessRepository.save(business);

        activityLogService.log("Updated business profile", "BUSINESS", business.getId());

        return BusinessResponse.from(business);
    }

    public BusinessResponse updateSlug(UpdateSlugRequest req) {
        String slug = req.slug().trim().toLowerCase();
        if (!SLUG_PATTERN.matcher(slug).matches() || slug.length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Booking page links can only use lowercase letters, numbers, and hyphens.");
        }

        Business business = getOwned();
        if (!slug.equals(business.getSlug()) && businessRepository.existsBySlug(slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "That link is already taken — try another.");
        }

        business.setSlug(slug);
        business = businessRepository.save(business);

        activityLogService.log("Updated the booking page link", "BUSINESS", business.getId());

        return BusinessResponse.from(business);
    }

    public BusinessResponse uploadLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No file was uploaded.");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Logo must be a PNG, JPEG, or WEBP image.");
        }

        Business business = getOwned();

        String extension = switch (file.getContentType()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String filename = business.getId() + extension;

        try {
            Path logosDir = Paths.get(uploadDir, "logos");
            Files.createDirectories(logosDir);
            Path target = logosDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Couldn't save the logo. Please try again.");
        }

        // Cache-bust with a timestamp query param so the browser doesn't keep
        // showing a stale cached logo after it's replaced with a new file at
        // the same path.
        business.setLogoUrl("/uploads/logos/" + filename + "?v=" + System.currentTimeMillis());
        business = businessRepository.save(business);

        activityLogService.log("Updated the business logo", "BUSINESS", business.getId());

        return BusinessResponse.from(business);
    }

    private Business getOwned() {
        return businessRepository.findById(TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
    }
}
