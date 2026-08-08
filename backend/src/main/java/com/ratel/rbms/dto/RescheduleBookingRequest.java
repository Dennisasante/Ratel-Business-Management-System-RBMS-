package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleBookingRequest(
        @NotNull(message = "Choose a new date and time")
        Instant scheduledAt
) {
}
