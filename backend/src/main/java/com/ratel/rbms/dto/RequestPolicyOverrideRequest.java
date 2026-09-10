package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RequestPolicyOverrideRequest(
        @NotNull(message = "commitmentReference is required") UUID commitmentReference,
        @NotNull(message = "policyId is required") UUID policyId,
        @NotNull(message = "policyVersionId is required") UUID policyVersionId,
        @NotBlank(message = "A reason is required") String reason
) {
}
