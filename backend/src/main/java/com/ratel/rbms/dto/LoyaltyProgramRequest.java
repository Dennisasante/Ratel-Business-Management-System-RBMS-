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
        // How many points one visit is worth. Null keeps the current value (1 for a new program).
        @Min(value = 1, message = "A visit must be worth at least 1 point")
        @Max(value = 1000, message = "Keep it to 1000 points or fewer")
        Integer pointsPerVisit,

        @NotBlank(message = "Describe the reward")
        @Size(max = 120, message = "Keep the reward under 120 characters")
        String rewardDescription
) {
}
