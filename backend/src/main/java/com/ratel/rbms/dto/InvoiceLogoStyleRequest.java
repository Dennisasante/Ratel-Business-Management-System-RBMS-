package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InvoiceLogoStyleRequest(
        @NotBlank(message = "Choose a logo style")
        @Pattern(regexp = "^(HEADER|WATERMARK)$", message = "Logo style must be HEADER or WATERMARK")
        String style
) {
}
