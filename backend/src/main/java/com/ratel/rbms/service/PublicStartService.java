package com.ratel.rbms.service;

import com.ratel.rbms.dto.StartHubConfigResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessIntegrations;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessIntegrationsRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomItemAttributeRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Backs the single hosted "hub" link (ratel.app/start/{slug}) a business puts
 * in its WhatsApp bio instead of juggling one link per feature (booking,
 * custom orders, and whatever ships next). Resolves the business once by
 * slug, then reports which entry points are actually usable today — plan
 * access alone isn't enough, since a business can have the feature on its
 * plan but never have configured anything for it to show (no bookable
 * services, no custom-order attributes) — same reasoning BookingService and
 * CustomWigRequestService already use for their own "enabled" flags, just
 * composed here across both.
 */
@Service
public class PublicStartService {

    private final BusinessRepository businessRepository;
    private final BusinessIntegrationsRepository businessIntegrationsRepository;
    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final CustomItemAttributeRepository customItemAttributeRepository;
    private final PlanFeatureService planFeatureService;
    private final WhatsAppLinkService whatsAppLinkService;

    public PublicStartService(
            BusinessRepository businessRepository,
            BusinessIntegrationsRepository businessIntegrationsRepository,
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            CustomItemAttributeRepository customItemAttributeRepository,
            PlanFeatureService planFeatureService,
            WhatsAppLinkService whatsAppLinkService
    ) {
        this.businessRepository = businessRepository;
        this.businessIntegrationsRepository = businessIntegrationsRepository;
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.customItemAttributeRepository = customItemAttributeRepository;
        this.planFeatureService = planFeatureService;
        this.whatsAppLinkService = whatsAppLinkService;
    }

    public StartHubConfigResponse getConfigBySlug(String slug) {
        Business business = businessRepository.findBySlug(slug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        UUID businessId = business.getId();

        boolean bookingEnabled = planFeatureService.hasFeature(businessId, PlanFeature.BOOKING_WIDGET)
                && serviceCatalogItemRepository.existsByBusinessIdAndActiveTrueAndBookableOnlineTrue(businessId);

        boolean customOrderEnabled = planFeatureService.hasFeature(businessId, PlanFeature.CUSTOM_WIG_REQUESTS)
                && customItemAttributeRepository.existsByBusinessId(businessId);

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(businessId).orElse(null);
        String businessWhatsappLink = integrations != null
                ? whatsAppLinkService.buildLink(integrations.getWhatsappNotifyNumber(),
                        "Hi " + business.getName() + ", I have a question.")
                : null;

        return new StartHubConfigResponse(business.getName(), bookingEnabled, customOrderEnabled, businessWhatsappLink);
    }
}
