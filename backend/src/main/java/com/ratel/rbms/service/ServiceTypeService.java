package com.ratel.rbms.service;

import com.ratel.rbms.dto.ServiceTypeRequest;
import com.ratel.rbms.dto.ServiceTypeResponse;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// Mirrors ProductCategoryService: business-managed list, renamed in place,
// removal blocked while any service order or catalog item still references it.
@Service
public class ServiceTypeService {

    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final ActivityLogService activityLogService;

    public ServiceTypeService(
            ServiceTypeRepository serviceTypeRepository,
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            ServiceOrderRepository serviceOrderRepository,
            ActivityLogService activityLogService
    ) {
        this.serviceTypeRepository = serviceTypeRepository;
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.activityLogService = activityLogService;
    }

    public List<ServiceTypeResponse> listAll() {
        UUID businessId = TenantContext.getBusinessId();
        return serviceTypeRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .map(t -> ServiceTypeResponse.from(t, usageCount(businessId, t.getId())))
                .toList();
    }

    public ServiceType getOwned(UUID id) {
        return serviceTypeRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service type not found."));
    }

    public ServiceTypeResponse create(ServiceTypeRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        if (serviceTypeRepository.existsByBusinessIdAndNameIgnoreCase(businessId, req.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "A service type with this name already exists.");
        }

        ServiceType type = ServiceType.builder()
                .businessId(businessId)
                .name(req.name())
                .build();
        type = serviceTypeRepository.save(type);

        activityLogService.log("Added service type \"" + type.getName() + "\"", "SERVICE_TYPE", type.getId());

        return ServiceTypeResponse.from(type, 0);
    }

    public ServiceTypeResponse rename(UUID id, ServiceTypeRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        ServiceType type = getOwned(id);

        if (!type.getName().equalsIgnoreCase(req.name())
                && serviceTypeRepository.existsByBusinessIdAndNameIgnoreCase(businessId, req.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "A service type with this name already exists.");
        }

        type.setName(req.name());
        type = serviceTypeRepository.save(type);

        return ServiceTypeResponse.from(type, usageCount(businessId, type.getId()));
    }

    public void delete(UUID id) {
        UUID businessId = TenantContext.getBusinessId();
        ServiceType type = getOwned(id);

        long count = usageCount(businessId, id);
        if (count > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    count + " item" + (count == 1 ? "" : "s") + " use" + (count == 1 ? "s" : "") + " this — reassign them first.");
        }

        serviceTypeRepository.delete(type);
        activityLogService.log("Removed service type \"" + type.getName() + "\"", "SERVICE_TYPE", id);
    }

    private long usageCount(UUID businessId, UUID typeId) {
        return serviceCatalogItemRepository.countByBusinessIdAndServiceTypeId(businessId, typeId)
                + serviceOrderRepository.countByBusinessIdAndServiceTypeId(businessId, typeId);
    }
}
