package com.ratel.rbms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoyaltyProgramRequest(
        @NotNull(message = "Say whether the program is on")
        Boolean enabled,

        @NotNull(message = "Set how many visits earn a reward")
        @Min(value = 1, message = "A reward needs at least 1 visit")
        @Max(value = 100, message = "Keep it to 100 visits or fewer")
        Integer visitsForReward,

        @NotBlank(message = "Describe the reward")
        @Size(max = 120, message = "Keep the reward under 120 characters")
        String rewardDescription
) {
}
