package com.ratel.rbms.dto;

public record AvailabilityCheckResponse(
        boolean available,
        String reason
) {
    public static AvailabilityCheckResponse ok() {
        return new AvailabilityCheckResponse(true, null);
    }

    public static AvailabilityCheckResponse unavailable(String reason) {
        return new AvailabilityCheckResponse(false, reason);
    }
}
