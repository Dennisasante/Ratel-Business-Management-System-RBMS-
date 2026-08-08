package com.ratel.rbms.service;

import com.ratel.rbms.entity.Business;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.SubscriptionPlanRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Gates the new modules (booking widget, WooCommerce sync, custom wig
 * requests) behind a business's plan — see PlanFeature for the codes.
 * Existing core functionality isn't gated by this at all; only controllers
 * for the new modules built alongside this call it.
 */
@Service
public class PlanFeatureService {

    private final BusinessRepository businessRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public PlanFeatureService(BusinessRepository businessRepository, SubscriptionPlanRepository subscriptionPlanRepository) {
        this.businessRepository = businessRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    /** For the current authenticated (owner/staff) request — throws 403 if the plan doesn't include this feature. */
    public void requireFeature(String featureCode) {
        requireFeature(TenantContext.getBusinessId(), featureCode);
    }

    public void requireFeature(UUID businessId, String featureCode) {
        if (!hasFeature(businessId, featureCode)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Upgrade your plan to use this.");
        }
    }

    /**
     * Non-throwing check — for public-facing endpoints (e.g. the booking
     * widget) that should report a plain "not available" to a customer
     * rather than an owner-facing "upgrade your plan" message.
     */
    public boolean hasFeature(UUID businessId, String featureCode) {
        Business business = businessRepository.findById(businessId).orElse(null);
        if (business == null || business.getSubscriptionPlanId() == null) {
            return false;
        }
        return subscriptionPlanRepository.findById(business.getSubscriptionPlanId())
                .map(plan -> plan.getFeatures().contains(featureCode))
                .orElse(false);
    }
}
