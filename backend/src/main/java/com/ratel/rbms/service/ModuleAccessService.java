package com.ratel.rbms.service;

import com.ratel.rbms.entity.Business;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Gates a module behind a specific business's own enabledModules list — a
 * Super Admin decision ("this business doesn't need Custom Wig Requests"),
 * deliberately independent of PlanFeatureService (which gates BOOKING_WIDGET/
 * WOOCOMMERCE_SYNC/CUSTOM_WIG_REQUESTS behind the business's subscription
 * PLAN instead). Where both exist for the same module, both must pass — they
 * answer different questions ("does the plan include this" vs. "did the
 * Super Admin turn this on for this business").
 *
 * INVENTORY/SALES/CUSTOMERS/EXPENSES are the permanent core set (Business's
 * own default enabledModules) and are never checked here — this service only
 * ever gates the optional modules a business might not need at all.
 */
@Service
public class ModuleAccessService {

    private final BusinessRepository businessRepository;

    public ModuleAccessService(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    public void requireModule(UUID businessId, String moduleCode) {
        if (!hasModule(businessId, moduleCode)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This isn't available for your business.");
        }
    }

    public boolean hasModule(UUID businessId, String moduleCode) {
        Business business = businessRepository.findById(businessId).orElse(null);
        return business != null && business.getEnabledModules().contains(moduleCode);
    }
}
