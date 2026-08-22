package com.ratel.rbms.service;

import com.ratel.rbms.dto.SupplierRequest;
import com.ratel.rbms.dto.SupplierResponse;
import com.ratel.rbms.entity.Supplier;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.SupplierRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ActivityLogService activityLogService;
    private final ModuleAccessService moduleAccessService;

    public SupplierService(SupplierRepository supplierRepository, ActivityLogService activityLogService, ModuleAccessService moduleAccessService) {
        this.supplierRepository = supplierRepository;
        this.activityLogService = activityLogService;
        this.moduleAccessService = moduleAccessService;
    }

    public List<SupplierResponse> listAll() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "SUPPLIERS_AND_PURCHASING");
        return supplierRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .map(SupplierResponse::from)
                .toList();
    }

    public Supplier getOwned(UUID id) {
        return supplierRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Supplier not found."));
    }

    public SupplierResponse create(SupplierRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "SUPPLIERS_AND_PURCHASING");
        Supplier supplier = Supplier.builder()
                .businessId(businessId)
                .name(req.name())
                .phone(req.phone())
                .email(req.email())
                .notes(req.notes())
                .build();
        supplier = supplierRepository.save(supplier);

        activityLogService.log("Added supplier \"" + supplier.getName() + "\"", "SUPPLIER", supplier.getId());

        return SupplierResponse.from(supplier);
    }
}
