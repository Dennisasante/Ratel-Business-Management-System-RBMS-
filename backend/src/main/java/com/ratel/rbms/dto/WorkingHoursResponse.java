package com.ratel.rbms.dto;

import com.ratel.rbms.entity.BusinessWorkingHours;

import java.time.LocalTime;

public record WorkingHoursResponse(int dayOfWeek, LocalTime startTime, LocalTime endTime) {
    public static WorkingHoursResponse from(BusinessWorkingHours hours) {
        return new WorkingHoursResponse(hours.getDayOfWeek(), hours.getStartTime(), hours.getEndTime());
    }
}
