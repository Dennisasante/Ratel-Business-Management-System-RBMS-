package com.ratel.rbms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A business's loyalty program: how many visits earn a reward, and what the reward is. */
@Entity
@Table(name = "loyalty_programs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyProgram {

    public static final int DEFAULT_VISITS_FOR_REWARD = 10;
    public static final String DEFAULT_REWARD = "a free service";

    @Id
    @Column(name = "business_id")
    private UUID businessId;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "visits_for_reward", nullable = false)
    @Builder.Default
    private int visitsForReward = DEFAULT_VISITS_FOR_REWARD;

    // How many points one visit is worth (points = visits x this).
    @Column(name = "points_per_visit", nullable = false)
    @Builder.Default
    private int pointsPerVisit = 1;

    @Column(name = "reward_description", nullable = false, length = 120)
    @Builder.Default
    private String rewardDescription = DEFAULT_REWARD;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
