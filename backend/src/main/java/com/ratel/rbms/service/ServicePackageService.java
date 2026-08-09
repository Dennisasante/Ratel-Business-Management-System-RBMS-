package com.ratel.rbms.service;

import com.ratel.rbms.dto.ServicePackageItemRequest;
import com.ratel.rbms.dto.ServicePackageItemResponse;
import com.ratel.rbms.dto.ServicePackageRequest;
import com.ratel.rbms.dto.ServicePackageResponse;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServicePackage;
import com.ratel.rbms.entity.ServicePackageItem;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServicePackageItemRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * A bundle of real service_catalog items sold/booked as one atomic unit (e.g.
 * a bridal package). Mirrors ServiceCatalogService's shape for the package
 * itself, and CustomItemAttributeService's "delete then rebuild" pattern for
 * the component item list, which is edited as a whole set on every save.
 */
@Service
public class ServicePackageService {

    private final ServicePackageRepository servicePackageRepository;
    private final ServicePackageItemRepository servicePackageItemRepository;
    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final ServiceTypeService serviceTypeService;
    private final ActivityLogService activityLogService;

    public ServicePackageService(
            ServicePackageRepository servicePackageRepository,
            ServicePackageItemRepository servicePackageItemRepository,
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            ServiceTypeService serviceTypeService,
            ActivityLogService activityLogService
    ) {
        this.servicePackageRepository = servicePackageRepository;
        this.servicePackageItemRepository = servicePackageItemRepository;
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.serviceTypeService = serviceTypeService;
        this.activityLogService = activityLogService;
    }

    public List<ServicePackageResponse> list() {
        UUID businessId = TenantContext.getBusinessId();
        return servicePackageRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ServicePackage getOwned(UUID id) {
        return servicePackageRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Package not found."));
    }

    @Transactional
    public ServicePackageResponse create(ServicePackageRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        ServiceType type = serviceTypeService.getOwned(req.serviceTypeId());

        ServicePackage pkg = ServicePackage.builder()
                .businessId(businessId)
                .serviceTypeId(type.getId())
                .name(req.name())
                .description(blankToNull(req.description()))
                .price(req.price())
                .bookableOnline(req.bookableOnline() != null && req.bookableOnline())
                .durationMinutes(req.durationMinutes() != null ? req.durationMinutes() : 60)
                .maxConcurrentBookings(req.maxConcurrentBookings() != null ? req.maxConcurrentBookings() : 1)
                .build();
        pkg = servicePackageRepository.save(pkg);
        saveItems(pkg.getId(), req.items());
        activityLogService.log("Added package \"" + pkg.getName() + "\"", "SERVICE_PACKAGE", pkg.getId());
        return toResponse(pkg);
    }

    @Transactional
    public ServicePackageResponse update(UUID id, ServicePackageRequest req) {
        ServicePackage pkg = getOwned(id);
        ServiceType type = serviceTypeService.getOwned(req.serviceTypeId());

        pkg.setServiceTypeId(type.getId());
        pkg.setName(req.name());
        pkg.setDescription(blankToNull(req.description()));
        pkg.setPrice(req.price());
        if (req.bookableOnline() != null) pkg.setBookableOnline(req.bookableOnline());
        if (req.durationMinutes() != null) pkg.setDurationMinutes(req.durationMinutes());
        if (req.maxConcurrentBookings() != null) pkg.setMaxConcurrentBookings(req.maxConcurrentBookings());
        pkg = servicePackageRepository.save(pkg);

        servicePackageItemRepository.deleteAllByPackageId(pkg.getId());
        saveItems(pkg.getId(), req.items());

        return toResponse(pkg);
    }

    public ServicePackageResponse setActive(UUID id, boolean active) {
        ServicePackage pkg = getOwned(id);
        pkg.setActive(active);
        pkg = servicePackageRepository.save(pkg);
        activityLogService.log(
                (active ? "Reactivated" : "Archived") + " package \"" + pkg.getName() + "\"",
                "SERVICE_PACKAGE", pkg.getId()
        );
        return toResponse(pkg);
    }

    private void saveItems(UUID packageId, List<ServicePackageItemRequest> items) {
        UUID businessId = TenantContext.getBusinessId();
        for (ServicePackageItemRequest itemReq : items) {
            ServiceCatalogItem catalogItem = serviceCatalogItemRepository.findByIdAndBusinessId(itemReq.serviceCatalogId(), businessId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "One of the selected services doesn't exist."));
            servicePackageItemRepository.save(ServicePackageItem.builder()
                    .packageId(packageId)
                    .serviceCatalogId(catalogItem.getId())
                    .quantity(itemReq.quantity() != null && itemReq.quantity() > 0 ? itemReq.quantity() : 1)
                    .build());
        }
    }

    private ServicePackageResponse toResponse(ServicePackage pkg) {
        String typeName = resolveTypeNameOrNull(pkg.getServiceTypeId());
        List<ServicePackageItemResponse> items = servicePackageItemRepository.findAllByPackageId(pkg.getId()).stream()
                .map(item -> new ServicePackageItemResponse(
                        item.getId(),
                        item.getServiceCatalogId(),
                        resolveCatalogNameOrNull(item.getServiceCatalogId()),
                        item.getQuantity()
                ))
                .toList();
        return ServicePackageResponse.from(pkg, typeName, items);
    }

    private String resolveTypeNameOrNull(UUID typeId) {
        try {
            return serviceTypeService.getOwned(typeId).getName();
        } catch (ApiException e) {
            return null;
        }
    }

    private String resolveCatalogNameOrNull(UUID catalogItemId) {
        return serviceCatalogItemRepository.findByIdAndBusinessId(catalogItemId, TenantContext.getBusinessId())
                .map(ServiceCatalogItem::getName)
                .orElse(null);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
