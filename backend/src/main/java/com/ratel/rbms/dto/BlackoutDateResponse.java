package com.ratel.rbms.dto;

import com.ratel.rbms.entity.BusinessBlackoutDate;

import java.time.LocalDate;
import java.util.UUID;

public record BlackoutDateResponse(
        UUID id,
        LocalDate date,
        String label
) {
    public static BlackoutDateResponse from(BusinessBlackoutDate d) {
        return new BlackoutDateResponse(d.getId(), d.getDate(), d.getLabel());
    }
}
