package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HelpRequestRequest(
        @NotBlank String category,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank String message
) {
}
