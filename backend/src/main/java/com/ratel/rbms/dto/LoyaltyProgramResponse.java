package com.ratel.rbms.dto;

public record LoyaltyProgramResponse(boolean enabled, int visitsForReward, int pointsPerVisit, String rewardDescription) {
}
