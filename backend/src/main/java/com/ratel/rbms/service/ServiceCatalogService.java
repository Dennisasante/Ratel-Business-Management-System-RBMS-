package com.ratel.rbms.service;

import com.ratel.rbms.dto.ServiceCatalogItemRequest;
import com.ratel.rbms.dto.ServiceCatalogItemResponse;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ServiceCatalogService {

    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final ServiceTypeService serviceTypeService;
    private final ActivityLogService activityLogService;

    public ServiceCatalogService(
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            ServiceTypeService serviceTypeService,
            ActivityLogService activityLogService
    ) {
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.serviceTypeService = serviceTypeService;
        this.activityLogService = activityLogService;
    }

    // activeOnly=true backs the order form's "pick a catalog price" dropdown;
    // false (the default) backs the catalog management page, which needs to
    // show archived items too so they can be reactivated.
    public List<ServiceCatalogItemResponse> listAll(boolean activeOnly) {
        List<ServiceCatalogItem> items = serviceCatalogItemRepository.findAllByBusinessIdOrderByNameAsc(TenantContext.getBusinessId());
        return items.stream()
                .filter(i -> !activeOnly || i.isActive())
                .map(this::toResponse)
                .toList();
    }

    public ServiceCatalogItem getOwned(UUID id) {
        return serviceCatalogItemRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Catalog item not found."));
    }

    public ServiceCatalogItemResponse create(ServiceCatalogItemRequest req) {
        ServiceType type = serviceTypeService.getOwned(req.serviceTypeId());

        ServiceCatalogItem item = ServiceCatalogItem.builder()
                .businessId(TenantContext.getBusinessId())
                .serviceTypeId(type.getId())
                .name(req.name())
                .price(req.price())
                .build();
        item = serviceCatalogItemRepository.save(item);
        activityLogService.log("Added service catalog item \"" + item.getName() + "\"", "SERVICE_CATALOG", item.getId());
        return ServiceCatalogItemResponse.from(item, type.getName());
    }

    public ServiceCatalogItemResponse update(UUID id, ServiceCatalogItemRequest req) {
        ServiceCatalogItem item = getOwned(id);
        ServiceType type = serviceTypeService.getOwned(req.serviceTypeId());

        item.setServiceTypeId(type.getId());
        item.setName(req.name());
        item.setPrice(req.price());
        item = serviceCatalogItemRepository.save(item);
        return ServiceCatalogItemResponse.from(item, type.getName());
    }

    // Archive/restore instead of delete — an order's service_catalog_id would otherwise
    // dangle, and reactivating a discontinued price later should be a toggle, not a redo.
    public ServiceCatalogItemResponse setActive(UUID id, boolean active) {
        ServiceCatalogItem item = getOwned(id);
        item.setActive(active);
        item = serviceCatalogItemRepository.save(item);
        activityLogService.log(
                (active ? "Reactivated" : "Archived") + " service catalog item \"" + item.getName() + "\"",
                "SERVICE_CATALOG", item.getId()
        );
        return toResponse(item);
    }

    private ServiceCatalogItemResponse toResponse(ServiceCatalogItem item) {
        String typeName = resolveTypeNameOrNull(item.getServiceTypeId());
        return ServiceCatalogItemResponse.from(item, typeName);
    }

    private String resolveTypeNameOrNull(UUID typeId) {
        try {
            return serviceTypeService.getOwned(typeId).getName();
        } catch (ApiException e) {
            return null; // the type may have been removed since this item was created
        }
    }
}
