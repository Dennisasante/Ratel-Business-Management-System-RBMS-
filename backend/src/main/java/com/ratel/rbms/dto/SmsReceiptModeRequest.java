package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SmsReceiptModeRequest(
        @NotBlank(message = "Choose a receipt option")
        @Pattern(regexp = "^(PAPER|SMS|BOTH)$", message = "Choose paper, SMS, or both")
        String mode
) {
}
