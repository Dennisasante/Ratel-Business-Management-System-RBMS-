package com.ratel.rbms.dto;

import java.util.List;

public record BookingSettingsResponse(
        String paymentPolicy,
        int depositPercent,
        boolean allowPayInPerson,
        int cancellationCutoffHours,
        List<WorkingHoursResponse> workingHours
) {
}
