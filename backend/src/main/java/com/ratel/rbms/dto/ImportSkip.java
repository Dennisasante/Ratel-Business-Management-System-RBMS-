package com.ratel.rbms.dto;

public record ImportSkip(
        int rowNumber,
        String name,
        String reason
) {
}
