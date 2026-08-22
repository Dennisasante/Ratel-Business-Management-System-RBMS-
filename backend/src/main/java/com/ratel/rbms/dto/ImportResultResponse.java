package com.ratel.rbms.dto;

import java.util.List;

public record ImportResultResponse(
        int importedCount,
        int skippedCount,
        List<ImportSkip> skipped
) {
}
