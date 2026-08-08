package com.ratel.rbms.dto;

import java.time.LocalTime;
import java.util.List;

public record BusinessIntegrationsResponse(
        String paystackPublicKey,
        boolean paystackSecretConfigured,
        String paystackSecretMasked,
        String woocommerceSiteUrl,
        boolean woocommerceConfigured,
        String woocommerceConsumerKeyMasked,
        boolean woocommerceWebhookRegistered,
        String whatsappNotifyNumber,
        boolean testMode,
        String bookingPaymentPolicy,
        int bookingDepositPercent,
        List<Integer> workingDays,
        LocalTime workingHoursStart,
        LocalTime workingHoursEnd
) {
}
