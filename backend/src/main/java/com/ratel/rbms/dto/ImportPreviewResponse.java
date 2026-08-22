package com.ratel.rbms.dto;

import java.util.List;

public record ImportPreviewResponse(
        List<ImportRow> rows,
        int validCount,
        int errorCount
) {
}
