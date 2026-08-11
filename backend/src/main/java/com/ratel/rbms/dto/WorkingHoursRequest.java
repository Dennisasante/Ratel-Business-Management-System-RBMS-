package com.ratel.rbms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record WorkingHoursRequest(
        @Min(value = 1, message = "Day of week must be 1 (Monday) through 7 (Sunday)")
        @Max(value = 7, message = "Day of week must be 1 (Monday) through 7 (Sunday)")
        int dayOfWeek,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime
) {
}
