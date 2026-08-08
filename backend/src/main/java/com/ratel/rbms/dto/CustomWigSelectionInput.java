package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CustomWigSelectionInput(
        @NotNull UUID attributeId,
        @NotNull UUID optionId
) {
}
