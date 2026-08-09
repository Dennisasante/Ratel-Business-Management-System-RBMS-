package com.ratel.rbms.dto;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record BookingWidgetConfigResponse(
        UUID businessId,
        String businessName,
        boolean enabled,
        String currency,
        String paystackPublicKey,
        String paymentPolicy,
        int depositPercent,
        boolean allowPayInPerson,
        List<Integer> workingDays,
        LocalTime workingHoursStart,
        LocalTime workingHoursEnd,
        String businessWhatsappLink
) {
}
