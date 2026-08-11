package com.ratel.rbms.dto;

import jakarta.validation.Valid;

import java.util.List;

/**
 * Same "null = leave unchanged" convention as BusinessIntegrationsRequest,
 * except workingHours: sending it always fully replaces the current set
 * (delete-then-rebuild, same pattern as ServicePackage's item list) — a
 * partial merge would be confusing for something the UI presents as
 * "here's the whole week." Omit the field (null) to leave hours untouched.
 */
public record BookingSettingsRequest(
        String paymentPolicy,
        Integer depositPercent,
        Boolean allowPayInPerson,
        Integer cancellationCutoffHours,
        List<@Valid WorkingHoursRequest> workingHours
) {
}
