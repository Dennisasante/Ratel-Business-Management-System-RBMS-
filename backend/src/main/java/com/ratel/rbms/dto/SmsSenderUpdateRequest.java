package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SmsSenderUpdateRequest(
        // 3-11 letters/digits — mNotify's own sender ID limit, no spaces.
        @Pattern(regexp = "^[A-Za-z0-9]{3,11}$", message = "Sender name must be 3-11 letters or digits, no spaces")
        String senderId,

        @NotBlank(message = "A status is required")
        @Pattern(regexp = "^(NONE|PENDING|APPROVED)$", message = "Status must be NONE, PENDING or APPROVED")
        String senderStatus
) {
}
