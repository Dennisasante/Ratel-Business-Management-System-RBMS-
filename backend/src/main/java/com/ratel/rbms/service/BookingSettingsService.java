package com.ratel.rbms.service;

import com.ratel.rbms.dto.BlackoutDateRequest;
import com.ratel.rbms.dto.BlackoutDateResponse;
import com.ratel.rbms.dto.BookingSettingsRequest;
import com.ratel.rbms.dto.BookingSettingsResponse;
import com.ratel.rbms.dto.WorkingHoursRequest;
import com.ratel.rbms.dto.WorkingHoursResponse;
import com.ratel.rbms.entity.BusinessBlackoutDate;
import com.ratel.rbms.entity.BusinessIntegrations;
import com.ratel.rbms.entity.BusinessWorkingHours;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessBlackoutDateRepository;
import com.ratel.rbms.repository.BusinessIntegrationsRepository;
import com.ratel.rbms.repository.BusinessWorkingHoursRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a business owner sets about how bookings behave — payment
 * policy, cancellation rules, working hours, blackout dates. Split out from
 * BusinessIntegrationsService, which now only covers genuine third-party
 * integration concerns (Paystack/WooCommerce/WhatsApp). Booking settings
 * still physically live on the business_integrations table/entity (no need
 * to move columns for this) — only the API boundary changed.
 */
@Service
public class BookingSettingsService {

    private static final Set<String> VALID_PAYMENT_POLICIES = Set.of("NONE", "DEPOSIT", "FULL");

    private final BusinessIntegrationsRepository businessIntegrationsRepository;
    private final BusinessWorkingHoursRepository businessWorkingHoursRepository;
    private final BusinessBlackoutDateRepository businessBlackoutDateRepository;

    public BookingSettingsService(
            BusinessIntegrationsRepository businessIntegrationsRepository,
            BusinessWorkingHoursRepository businessWorkingHoursRepository,
            BusinessBlackoutDateRepository businessBlackoutDateRepository
    ) {
        this.businessIntegrationsRepository = businessIntegrationsRepository;
        this.businessWorkingHoursRepository = businessWorkingHoursRepository;
        this.businessBlackoutDateRepository = businessBlackoutDateRepository;
    }

    public BookingSettingsResponse get() {
        UUID businessId = TenantContext.getBusinessId();
        BusinessIntegrations integrations = getOrCreate(businessId);
        return toResponse(integrations, resolveWorkingHours(businessId));
    }

    @Transactional
    public BookingSettingsResponse update(BookingSettingsRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        BusinessIntegrations integrations = getOrCreate(businessId);

        if (req.paymentPolicy() != null) {
            if (!VALID_PAYMENT_POLICIES.contains(req.paymentPolicy())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Payment policy must be NONE, DEPOSIT, or FULL.");
            }
            integrations.setBookingPaymentPolicy(req.paymentPolicy());
        }
        if (req.depositPercent() != null) {
            if (req.depositPercent() < 1 || req.depositPercent() > 99) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Deposit percentage must be between 1 and 99.");
            }
            integrations.setBookingDepositPercent(req.depositPercent());
        }
        if (req.allowPayInPerson() != null) {
            integrations.setAllowPayInPerson(req.allowPayInPerson());
        }
        if (req.cancellationCutoffHours() != null) {
            if (req.cancellationCutoffHours() < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Cancellation cutoff can't be negative.");
            }
            integrations.setCancellationCutoffHours(req.cancellationCutoffHours());
        }
        businessIntegrationsRepository.save(integrations);

        if (req.workingHours() != null) {
            for (WorkingHoursRequest day : req.workingHours()) {
                if (!day.startTime().isBefore(day.endTime())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Opening time must be before closing time.");
                }
            }
            // Flush the delete before inserting the replacement rows — Hibernate's
            // default flush order runs insertions before deletions, which would
            // otherwise collide with the (business_id, day_of_week) unique constraint
            // on a day that's unchanged (e.g. Monday deleted-and-reinserted with the
            // same key in the same flush).
            businessWorkingHoursRepository.deleteAllByBusinessId(businessId);
            businessWorkingHoursRepository.flush();
            for (WorkingHoursRequest day : req.workingHours()) {
                businessWorkingHoursRepository.save(BusinessWorkingHours.builder()
                        .businessId(businessId)
                        .dayOfWeek(day.dayOfWeek())
                        .startTime(day.startTime())
                        .endTime(day.endTime())
                        .build());
            }
        }

        return toResponse(integrations, resolveWorkingHours(businessId));
    }

    // Mirrors BookingService's own fallback — a business that's never saved
    // hours shouldn't look "closed every day" here while the booking widget
    // silently accepts bookings Mon-Sat 9-6 by default; the two must agree.
    private List<BusinessWorkingHours> resolveWorkingHours(UUID businessId) {
        List<BusinessWorkingHours> hours = businessWorkingHoursRepository.findAllByBusinessIdOrderByDayOfWeek(businessId);
        return hours.isEmpty() ? BusinessWorkingHours.defaultHours() : hours;
    }

    public List<BlackoutDateResponse> listBlackoutDates() {
        return businessBlackoutDateRepository.findAllByBusinessIdOrderByDateAsc(TenantContext.getBusinessId()).stream()
                .map(BlackoutDateResponse::from)
                .toList();
    }

    public BlackoutDateResponse addBlackoutDate(BlackoutDateRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        if (businessBlackoutDateRepository.existsByBusinessIdAndDate(businessId, req.date())) {
            throw new ApiException(HttpStatus.CONFLICT, "That date is already marked off.");
        }
        BusinessBlackoutDate blackout = BusinessBlackoutDate.builder()
                .businessId(businessId)
                .date(req.date())
                .label(req.label())
                .build();
        return BlackoutDateResponse.from(businessBlackoutDateRepository.save(blackout));
    }

    public void removeBlackoutDate(UUID id) {
        BusinessBlackoutDate blackout = businessBlackoutDateRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Blackout date not found."));
        businessBlackoutDateRepository.delete(blackout);
    }

    private BusinessIntegrations getOrCreate(UUID businessId) {
        return businessIntegrationsRepository.findByBusinessId(businessId)
                .orElseGet(() -> businessIntegrationsRepository.save(
                        BusinessIntegrations.builder().businessId(businessId).build()));
    }

    private BookingSettingsResponse toResponse(BusinessIntegrations i, List<BusinessWorkingHours> hours) {
        return new BookingSettingsResponse(
                i.getBookingPaymentPolicy(),
                i.getBookingDepositPercent(),
                i.isAllowPayInPerson(),
                i.getCancellationCutoffHours(),
                hours.stream().map(WorkingHoursResponse::from).toList()
        );
    }
}
